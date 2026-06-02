package com.hub.gisdatahub.opendata.collect.service;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hub.gisdatahub.opendata.collect.client.DataCollectClient;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class DashboardGisOpenApiCollectService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STATS_YM_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_NUM_OF_ROWS = 5;
    private static final int MAX_NUM_OF_ROWS = 100;
    private static final String EV_CHARGER_COUNT_METRIC_CODE = "EV_CHARGER_COUNT";
    private static final String EV_CHARGER_REGION_STATS_TYPE = "SIDO_DISTRIBUTION";
    private static final Duration EV_CHARGER_STATS_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int EV_CHARGER_STATS_FAILURE_BREAK_COUNT = 3;

    private final DataCollectClient dataCollectClient;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final DashboardGisObservationSyncService dashboardGisObservationSyncService;

    public DashboardGisOpenApiCollectService(
            DataCollectClient dataCollectClient,
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Environment environment,
            DashboardGisObservationSyncService dashboardGisObservationSyncService) {
        this.dataCollectClient = dataCollectClient;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.dashboardGisObservationSyncService = dashboardGisObservationSyncService;
    }

    @Transactional
    public Map<String, Object> collect(
            String sourceCode,
            Integer pageNo,
            Integer numOfRows,
            String statsYm,
            String keyword) {
        int resolvedPageNo = normalizePageNo(pageNo);
        int resolvedNumOfRows = normalizeNumOfRows(numOfRows);
        String resolvedStatsYm = normalizeStatsYm(statsYm);
        String resolvedKeyword = normalizeKeyword(keyword);
        List<SourceSpec> targets = resolveTargets(sourceCode);
        List<Map<String, Object>> results = new ArrayList<>();

        for (SourceSpec spec : targets) {
            results.add(collectOne(spec, resolvedPageNo, resolvedNumOfRows, resolvedStatsYm, resolvedKeyword));
        }

        long completed = results.stream().filter(result -> "COMPLETED".equals(result.get("status"))).count();
        long skipped = results.stream().filter(result -> "SKIPPED".equals(result.get("status"))).count();
        long failed = results.stream().filter(result -> "FAILED".equals(result.get("status"))).count();
        long noData = results.stream().filter(result -> "NO_DATA".equals(result.get("status"))).count();

        return Map.of(
                "pageNo", resolvedPageNo,
                "numOfRows", resolvedNumOfRows,
                "statsYm", resolvedStatsYm,
                "keyword", resolvedKeyword,
                "total", results.size(),
                "completed", completed,
                "noData", noData,
                "skipped", skipped,
                "failed", failed,
                "results", results);
    }

    public List<Map<String, Object>> collectStatus() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SourceSpec spec : SourceSpec.values()) {
            Map<String, Object> counts = countStoredRows(spec.datasetCode());
            String status;
            String reason = spec.blockerReason();
            long observationCount = number(counts.get("observation_count"));
            long featureCount = number(counts.get("feature_count"));
            long layerCount = number(counts.get("layer_count"));
            if (observationCount + featureCount + layerCount > 0) {
                status = "COLLECTED";
            } else if (reason != null && !reason.isBlank()) {
                status = "BLOCKED";
            } else {
                status = "NOT_COLLECTED";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceCode", spec.sourceCode());
            row.put("datasetCode", spec.datasetCode());
            row.put("storage", spec.storageTable());
            row.put("status", status);
            row.put("observationCount", observationCount);
            row.put("featureCount", featureCount);
            row.put("layerCount", layerCount);
            row.put("blocker", reason == null ? "" : reason);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> collectEvChargerRegionStats() {
        SourceSpec spec = SourceSpec.KECO_EV_CHARGER_MAIN;
        ensureEvChargerCountMetric();

        Map<String, Object> runParams = requestParams(spec, 1, 10, normalizeStatsYm(null), normalizeKeyword(null));
        runParams.put("statsType", EV_CHARGER_REGION_STATS_TYPE);
        runParams.put("zcodeScope", "SIDO");
        String missingKey = ensureAuthParams(spec, runParams);
        if (missingKey != null) {
            long runId = startRun(spec, runParams);
            String blocker = "missing API key: " + missingKey;
            finishRun(runId, "SKIPPED", 0, 0, 1, blocker);
            return Map.of(
                    "sourceCode", spec.sourceCode(),
                    "datasetCode", spec.datasetCode(),
                    "status", "SKIPPED",
                    "message", blocker,
                    "results", List.of());
        }

        long runId = startRun(spec, runParams);
        List<SidoArea> sidoAreas = findSidoAreas();
        List<RegionCount> regionCounts = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int consecutiveFailures = 0;

        for (SidoArea area : sidoAreas) {
            try {
                RegionCount count = fetchEvChargerRegionCount(spec, area);
                regionCounts.add(count);
                consecutiveFailures = 0;
            } catch (Exception exception) {
                consecutiveFailures++;
                failures.add(Map.of(
                        "areaCode", area.areaCode(),
                        "areaName", area.name(),
                        "sidoCode", area.sidoCode(),
                        "message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                if (consecutiveFailures >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT) {
                    failures.add(Map.of(
                            "message", "전기차 충전소 API 연속 응답 실패로 나머지 시도 집계를 중단했습니다."));
                    break;
                }
            }
        }

        int inserted = 0;
        BigDecimal totalCount = regionCounts.stream()
                .map(RegionCount::count)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!regionCounts.isEmpty()) {
            deleteEvChargerRegionStats();
            for (RegionCount regionCount : regionCounts) {
                inserted += insertEvChargerRegionStat(runId, regionCount, totalCount);
            }
        }

        String runStatus;
        String responseStatus;
        if (failures.isEmpty()) {
            runStatus = "SUCCEEDED";
            responseStatus = "COMPLETED";
        } else if (inserted > 0) {
            runStatus = "PARTIAL";
            responseStatus = "PARTIAL";
        } else {
            runStatus = "FAILED";
            responseStatus = "FAILED";
        }
        String message = failures.isEmpty()
                ? "전기차 충전소 시도별 전체 건수 통계를 저장했습니다."
                : "일부 시도 집계에 실패했습니다.";
        finishRun(runId, runStatus, regionCounts.size(), inserted, failures.size(), failures.isEmpty() ? null : message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sourceCode", spec.sourceCode());
        response.put("datasetCode", spec.datasetCode());
        response.put("metricCode", EV_CHARGER_COUNT_METRIC_CODE);
        response.put("status", responseStatus);
        response.put("statsType", EV_CHARGER_REGION_STATS_TYPE);
        response.put("regionCount", regionCounts.size());
        response.put("totalCount", totalCount);
        response.put("savedCount", inserted);
        response.put("failures", failures);
        response.put("results", regionCounts.stream()
                .map(regionCount -> Map.<String, Object>of(
                        "areaCode", regionCount.area().areaCode(),
                        "areaName", regionCount.area().name(),
                        "sidoCode", regionCount.area().sidoCode(),
                        "count", regionCount.count()))
                .toList());
        response.put("message", message);
        return response;
    }

    private Map<String, Object> collectOne(SourceSpec spec, int pageNo, int numOfRows, String statsYm, String keyword) {
        if (spec == SourceSpec.MOIS_ADMM_SEXD_AGE_PPLTN_MAIN) {
            return syncExistingResidentPopulation(spec, statsYm);
        }
        if (spec.blockerReason() != null && !spec.blockerReason().isBlank()) {
            long runId = startRun(spec, requestParams(spec, pageNo, numOfRows, statsYm, keyword));
            finishRun(runId, "SKIPPED", 0, 0, 1, spec.blockerReason());
            return result(spec, "SKIPPED", 0, 0, spec.blockerReason());
        }

        Map<String, Object> queryParams = requestParams(spec, pageNo, numOfRows, statsYm, keyword);
        String missingKey = ensureAuthParams(spec, queryParams);
        if (missingKey != null) {
            long runId = startRun(spec, queryParams);
            String blocker = "missing API key: " + missingKey;
            finishRun(runId, "SKIPPED", 0, 0, 1, blocker);
            return result(spec, "SKIPPED", 0, 0, blocker);
        }

        long runId = startRun(spec, queryParams);
        try {
            String body = dataCollectClient.callOpenApi(spec.baseUrl(), spec.path(), queryParams);
            SaveResult saveResult = saveResponse(runId, spec, body);
            String status = saveResult.savedCount() > 0 ? "COMPLETED" : "NO_DATA";
            finishRun(runId, status.equals("COMPLETED") ? "SUCCEEDED" : "SKIPPED", saveResult.fetchedCount(), saveResult.savedCount(), 0, null);
            return result(spec, status, saveResult.fetchedCount(), saveResult.savedCount(), saveResult.note());
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            finishRun(runId, "FAILED", 0, 0, 1, message);
            return result(spec, "FAILED", 0, 0, message);
        }
    }

    private Map<String, Object> syncExistingResidentPopulation(SourceSpec spec, String statsYm) {
        try {
            Map<String, Object> syncResult = dashboardGisObservationSyncService.syncResidentPopulation(statsYm, "1");
            Number inserted = (Number) syncResult.getOrDefault("insertedCount", 0);
            return result(spec, inserted.longValue() > 0 ? "COMPLETED" : "NO_DATA", 0, inserted.intValue(), "existing sd_resident_population synchronized");
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return result(spec, "FAILED", 0, 0, message);
        }
    }

    private void ensureEvChargerCountMetric() {
        String sql = """
                INSERT INTO public.sd_dashboard_metric (
                    dataset_code, metric_code, metric_name, value_type, unit, chart_group,
                    sort_order, is_default, metadata, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, '시도별 충전기 수', 'NUMBER', '건', '전기차 충전소',
                    100, FALSE, CAST(:metadata AS jsonb), CURRENT_TIMESTAMP
                )
                ON CONFLICT (dataset_code, metric_code) DO UPDATE SET
                    metric_name = EXCLUDED.metric_name,
                    value_type = EXCLUDED.value_type,
                    unit = EXCLUDED.unit,
                    chart_group = EXCLUDED.chart_group,
                    sort_order = EXCLUDED.sort_order,
                    metadata = EXCLUDED.metadata,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try {
            jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("datasetCode", SourceSpec.KECO_EV_CHARGER_MAIN.datasetCode())
                    .addValue("metricCode", EV_CHARGER_COUNT_METRIC_CODE)
                    .addValue("metadata", json(Map.of(
                            "collector", "dashboard-gis",
                            "statsType", EV_CHARGER_REGION_STATS_TYPE,
                            "displayLimit", 500))));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("전기차 충전소 통계 지표 JSON 변환 실패", exception);
        }
    }

    private List<SidoArea> findSidoAreas() {
        String sql = """
                SELECT area_code, sido_code, name, full_name
                FROM public.sd_area_code
                WHERE level = 'SIDO'
                  AND is_active = TRUE
                  AND sido_code IS NOT NULL
                ORDER BY sido_code
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new SidoArea(
                rs.getString("area_code"),
                rs.getString("sido_code"),
                rs.getString("name"),
                rs.getString("full_name")));
    }

    private RegionCount fetchEvChargerRegionCount(SourceSpec spec, SidoArea area) {
        Map<String, Object> queryParams = requestParams(spec, 1, 10, normalizeStatsYm(null), normalizeKeyword(null));
        String missingKey = ensureAuthParams(spec, queryParams);
        if (missingKey != null) {
            throw new IllegalStateException("missing API key: " + missingKey);
        }
        queryParams.put("zcode", area.sidoCode());

        String body = dataCollectClient.callOpenApi(
                spec.baseUrl(),
                spec.path(),
                queryParams,
                Duration.ofSeconds(5),
                EV_CHARGER_STATS_READ_TIMEOUT);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("empty response");
        }
        if (looksLikeApiError(body)) {
            throw new IllegalStateException(abbreviate(body));
        }

        JsonNode root = readJsonOrNull(body);
        if (root == null) {
            throw new IllegalStateException("전기차 충전소 API JSON 응답을 파싱하지 못했습니다.");
        }
        if (hasJsonApiError(root)) {
            throw new IllegalStateException(abbreviate(root.toString()));
        }

        BigDecimal totalCount = extractTotalCount(root)
                .orElseThrow(() -> new IllegalStateException("totalCount 값이 응답에 없습니다."));
        return new RegionCount(area, totalCount, root);
    }

    private Optional<BigDecimal> extractTotalCount(JsonNode root) {
        for (String path : List.of("totalCount", "response.body.totalCount", "body.totalCount")) {
            BigDecimal value = decimal(path(root, path));
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private void deleteEvChargerRegionStats() {
        String sql = """
                DELETE FROM public.sd_dashboard_area_observation
                WHERE dataset_code = :datasetCode
                  AND metric_code = :metricCode
                  AND dimensions ->> 'statsType' = :statsType
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("datasetCode", SourceSpec.KECO_EV_CHARGER_MAIN.datasetCode())
                .addValue("metricCode", EV_CHARGER_COUNT_METRIC_CODE)
                .addValue("statsType", EV_CHARGER_REGION_STATS_TYPE));
    }

    private int insertEvChargerRegionStat(long runId, RegionCount regionCount, BigDecimal apiTotalCount) {
        String sql = """
                INSERT INTO public.sd_dashboard_area_observation (
                    dataset_code, metric_code, collection_run_id, area_code, area_level, source_area_code,
                    source_area_name, base_date, observed_at, numeric_value, unit, dimensions, raw_payload,
                    created_at, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, :runId, :areaCode, 'SIDO', :sourceAreaCode,
                    :sourceAreaName, CURRENT_DATE, :observedAt, :numericValue, '건',
                    CAST(:dimensions AS jsonb), CAST(:rawPayload AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """;
        try {
            return jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("datasetCode", SourceSpec.KECO_EV_CHARGER_MAIN.datasetCode())
                    .addValue("metricCode", EV_CHARGER_COUNT_METRIC_CODE)
                    .addValue("runId", runId)
                    .addValue("areaCode", regionCount.area().areaCode())
                    .addValue("sourceAreaCode", regionCount.area().sidoCode())
                    .addValue("sourceAreaName", regionCount.area().name())
                    .addValue("observedAt", LocalDateTime.now(SEOUL_ZONE))
                    .addValue("numericValue", regionCount.count())
                    .addValue("dimensions", json(Map.of(
                            "collector", "dashboard-gis",
                            "sourceCode", SourceSpec.KECO_EV_CHARGER_MAIN.sourceCode(),
                            "statsType", EV_CHARGER_REGION_STATS_TYPE,
                            "apiTotalCount", apiTotalCount,
                            "displayFeatureLimit", 500,
                            "zcode", regionCount.area().sidoCode())))
                    .addValue("rawPayload", regionCount.rawPayload().toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("전기차 충전소 시도별 통계 JSON 변환 실패", exception);
        }
    }

    private SaveResult saveResponse(long runId, SourceSpec spec, String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            return new SaveResult(0, 0, "empty response");
        }
        if (looksLikeApiError(body)) {
            throw new IllegalStateException(abbreviate(body));
        }
        if (spec.storageType() == StorageType.LAYER) {
            int saved = insertLayer(runId, spec, body);
            return new SaveResult(1, saved, "layer metadata saved");
        }

        JsonNode root = readJsonOrNull(body);
        if (root == null) {
            List<JsonNode> xmlRows = readXmlRows(body);
            if (!xmlRows.isEmpty()) {
                int saved = 0;
                for (JsonNode row : xmlRows) {
                    saved += spec.storageType() == StorageType.FEATURE
                            ? insertFeature(runId, spec, row)
                            : insertObservation(runId, spec, row);
                }
                return new SaveResult(xmlRows.size(), saved, "parsed xml rows saved");
            }
            int saved = insertMetadataFeature(runId, spec, body);
            return new SaveResult(1, saved, "non-json response saved as metadata");
        }
        if (hasJsonApiError(root)) {
            throw new IllegalStateException(abbreviate(root.toString()));
        }

        List<JsonNode> rows = extractRows(root);
        int saved = 0;
        if (rows.isEmpty()) {
            saved = spec.storageType() == StorageType.FEATURE
                    ? insertMetadataFeature(runId, spec, root.toString())
                    : insertObservation(runId, spec, root);
            return new SaveResult(1, saved, "root response saved");
        }

        for (JsonNode row : rows) {
            saved += spec.storageType() == StorageType.FEATURE
                    ? insertFeature(runId, spec, row)
                    : insertObservation(runId, spec, row);
        }
        return new SaveResult(rows.size(), saved, "parsed rows saved");
    }

    private int insertFeature(long runId, SourceSpec spec, JsonNode row) throws JsonProcessingException {
        String sql = """
                INSERT INTO public.sd_dashboard_geo_feature (
                    dataset_code, metric_code, collection_run_id, external_id, feature_name, feature_category,
                    source_area_code, source_area_name, address, road_address, longitude, latitude, source_crs,
                    geom, base_date, observed_at, numeric_value, text_value, json_value, unit, dimensions,
                    raw_payload, created_at, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, :runId, :externalId, :featureName, :featureCategory,
                    :sourceAreaCode, :sourceAreaName, :address, :roadAddress, :longitude, :latitude, :sourceCrs,
                    CASE WHEN :longitude IS NOT NULL AND :latitude IS NOT NULL
                        THEN ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
                        ELSE NULL
                    END,
                    CURRENT_DATE, CURRENT_TIMESTAMP, :numericValue, :textValue, CAST(:jsonValue AS jsonb), :unit,
                    CAST(:dimensions AS jsonb), CAST(:rawPayload AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING
                """;
        BigDecimal longitude = firstDecimal(row, "longitude", "lon", "lng", "mapx", "mapX", "경도", "lo", "x", "LONGITUDE", "LON", "LNG");
        BigDecimal latitude = firstDecimal(row, "latitude", "lat", "mapy", "mapY", "위도", "la", "y", "LATITUDE", "LAT");
        String externalId = featureExternalId(row, spec);
        if (externalId.isBlank()) {
            externalId = spec.datasetCode() + ':' + hash(row.toString());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("metricCode", metricCode(spec.datasetCode()))
                .addValue("runId", runId)
                .addValue("externalId", externalId)
                .addValue("featureName", blankToNull(firstText(row, "name", "title", "featureName", "bizesNm", "statNm", "csNm", "fcltyNm", "시설명", "상호명", "관광지명", "schoolNm", "LBRRY_NM", "prkplceNm")))
                .addValue("featureCategory", blankToNull(firstText(row, "category", "contenttypeid", "indsSclsNm", "chgerType", "busiNm", "fcltyType", "type", "구분")))
                .addValue("sourceAreaCode", blankToNull(firstText(row, "areaCode", "areacode", "sigungucode", "ctprvnCd", "signguCd", "adongCd", "bjd_cd", "zscode", "zcode")))
                .addValue("sourceAreaName", blankToNull(firstText(row, "areaName", "addr1", "ctprvnNm", "signguNm", "sido_sgg_nm", "institutionNm", "instt_nm", "zcodeNm", "zscodeNm")))
                .addValue("address", blankToNull(firstText(row, "addr", "addr1", "address", "lnmadr", "lnoAdr", "LCTN_LOTNO_ADDR", "소재지주소")))
                .addValue("roadAddress", blankToNull(firstText(row, "roadAddress", "rdnmadr", "rdnmAdr", "LCTN_ROAD_NM_ADDR", "도로명주소")))
                .addValue("longitude", longitude)
                .addValue("latitude", latitude)
                .addValue("sourceCrs", "EPSG:4326")
                .addValue("numericValue", firstDecimal(row, "value", "count", "cnt", "CAPA", "ar", "parkingchrgeInfo"))
                .addValue("textValue", null)
                .addValue("jsonValue", row.toString())
                .addValue("unit", null)
                .addValue("dimensions", json(Map.of("collector", "dashboard-gis", "sourceCode", spec.sourceCode())))
                .addValue("rawPayload", row.toString());
        deleteExistingFeature(params);
        return jdbcTemplate.update(sql, params);
    }

    private void deleteExistingFeature(MapSqlParameterSource params) {
        String externalId = (String) params.getValue("externalId");
        if (externalId == null || externalId.isBlank()) {
            return;
        }
        String sql = """
                DELETE FROM public.sd_dashboard_geo_feature
                WHERE dataset_code = :datasetCode
                  AND metric_code IS NOT DISTINCT FROM :metricCode
                  AND external_id = :externalId
                """;
        jdbcTemplate.update(sql, params);
    }

    private int insertMetadataFeature(long runId, SourceSpec spec, String payload) throws JsonProcessingException {
        String externalId = spec.datasetCode() + ":metadata:" + hash(payload);
        String sql = """
                INSERT INTO public.sd_dashboard_geo_feature (
                    dataset_code, metric_code, collection_run_id, external_id, feature_name, feature_category,
                    base_date, observed_at, text_value, json_value, dimensions, raw_payload, created_at, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, :runId, :externalId, :featureName, 'METADATA', CURRENT_DATE,
                    CURRENT_TIMESTAMP, :textValue, CAST(:jsonValue AS jsonb), CAST(:dimensions AS jsonb),
                    CAST(:rawPayload AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING
                """;
        String jsonPayload = json(Map.of("raw", abbreviate(payload)));
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("metricCode", metricCode(spec.datasetCode()))
                .addValue("runId", runId)
                .addValue("externalId", externalId)
                .addValue("featureName", spec.datasetCode() + " metadata")
                .addValue("textValue", abbreviate(payload))
                .addValue("jsonValue", jsonPayload)
                .addValue("dimensions", json(Map.of("collector", "dashboard-gis", "sourceCode", spec.sourceCode())))
                .addValue("rawPayload", jsonPayload));
    }

    private int insertObservation(long runId, SourceSpec spec, JsonNode row) throws JsonProcessingException {
        String sql = """
                INSERT INTO public.sd_dashboard_area_observation (
                    dataset_code, metric_code, collection_run_id, area_code, area_level, source_area_code,
                    source_area_name, grid_x, grid_y, base_date, base_hour, observed_at, numeric_value,
                    text_value, json_value, unit, dimensions, raw_payload, created_at, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, :runId, NULL, :areaLevel, :sourceAreaCode, :sourceAreaName,
                    :gridX, :gridY, :baseDate, :baseHour, CURRENT_TIMESTAMP, :numericValue, :textValue,
                    CAST(:jsonValue AS jsonb), :unit, CAST(:dimensions AS jsonb), CAST(:rawPayload AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING
                """;
        BigDecimal numericValue = firstDecimal(row, "value", "val", "data", "cnt", "count", "totalCount", "totNmprCnt", "totPpltn", "population", "avgAge", "hhCnt", "numOfRows");
        String sourceAreaCode = firstText(row, "areaCode", "admmCd", "admCd", "ctpvCd", "sggCd", "dongCd", "법정동코드");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("metricCode", metricCode(spec.datasetCode()))
                .addValue("runId", runId)
                .addValue("areaLevel", blankToNull(spec.defaultAreaLevel()))
                .addValue("sourceAreaCode", blankToNull(sourceAreaCode))
                .addValue("sourceAreaName", blankToNull(firstText(row, "areaName", "ctpvNm", "sggNm", "dongNm", "sidoName", "stationName", "stnNm", "addr")))
                .addValue("gridX", blankToNull(firstText(row, "nx", "gridX")))
                .addValue("gridY", blankToNull(firstText(row, "ny", "gridY")))
                .addValue("baseDate", baseDate(row))
                .addValue("baseHour", baseHour(row))
                .addValue("numericValue", numericValue)
                .addValue("textValue", numericValue == null ? abbreviate(row.toString()) : null)
                .addValue("jsonValue", row.toString())
                .addValue("unit", null)
                .addValue("dimensions", json(Map.of("collector", "dashboard-gis", "sourceCode", spec.sourceCode())))
                .addValue("rawPayload", row.toString());
        return jdbcTemplate.update(sql, params);
    }

    private int insertLayer(long runId, SourceSpec spec, String body) throws JsonProcessingException {
        String sql = """
                INSERT INTO public.sd_dashboard_layer_catalog (
                    dataset_code, layer_code, layer_name, service_type, service_url, layer_name_on_server,
                    raw_capability, is_enabled, created_at, updated_at
                ) VALUES (
                    :datasetCode, :layerCode, :layerName, :serviceType, :serviceUrl, :layerNameOnServer,
                    CAST(:rawCapability AS jsonb), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT (dataset_code, layer_code) DO UPDATE SET
                    service_url = EXCLUDED.service_url,
                    raw_capability = EXCLUDED.raw_capability,
                    updated_at = CURRENT_TIMESTAMP
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("layerCode", spec.datasetCode() + ":default")
                .addValue("layerName", spec.datasetCode())
                .addValue("serviceType", "WFS")
                .addValue("serviceUrl", spec.baseUrl() + spec.path())
                .addValue("layerNameOnServer", null)
                .addValue("rawCapability", json(Map.of("collectionRunId", runId, "raw", abbreviate(body)))));
    }

    private Map<String, Object> requestParams(SourceSpec spec, int pageNo, int numOfRows, String statsYm, String keyword) {
        Map<String, Object> params = new LinkedHashMap<>(spec.params());
        params.putIfAbsent("pageNo", pageNo);
        params.putIfAbsent("numOfRows", numOfRows);
        params.replaceAll((key, value) -> replaceTokens(String.valueOf(value), statsYm, keyword));
        applySourceParamRules(spec, params);
        return params;
    }

    private void applySourceParamRules(SourceSpec spec, Map<String, Object> params) {
        if (spec == SourceSpec.KECO_EV_CHARGER_MAIN && intValue(params.get("numOfRows")) < 10) {
            params.put("numOfRows", 10);
        }
    }

    private String ensureAuthParams(SourceSpec spec, Map<String, Object> params) {
        for (AuthParam authParam : spec.authParams()) {
            String value = configuredValue(authParam.envName());
            if (value.isBlank()) {
                return authParam.envName();
            }
            params.put(authParam.paramName(), value);
        }
        return null;
    }

    private long startRun(SourceSpec spec, Map<String, Object> params) {
        String sql = """
                INSERT INTO public.sd_dashboard_collection_run (
                    source_code, dataset_code, status, request_params, started_at, created_at
                ) VALUES (
                    :sourceCode, :datasetCode, 'RUNNING', CAST(:params AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) RETURNING collection_run_id
                """;
        try {
            return Objects.requireNonNull(jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("sourceCode", spec.sourceCode())
                    .addValue("datasetCode", spec.datasetCode())
                    .addValue("params", json(redactParams(params))), Long.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("수집 파라미터 JSON 변환 실패", exception);
        }
    }

    private void finishRun(long runId, String status, int fetched, int inserted, int skipped, String error) {
        String sql = """
                UPDATE public.sd_dashboard_collection_run
                SET status = :status,
                    fetched_count = :fetched,
                    inserted_count = :inserted,
                    skipped_count = :skipped,
                    error_message = :error,
                    finished_at = CURRENT_TIMESTAMP
                WHERE collection_run_id = :runId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("fetched", fetched)
                .addValue("inserted", inserted)
                .addValue("skipped", skipped)
                .addValue("error", abbreviate(error))
                .addValue("runId", runId));
    }

    private String metricCode(String datasetCode) {
        String sql = """
                SELECT metric_code
                FROM public.sd_dashboard_metric
                WHERE dataset_code = :datasetCode
                ORDER BY is_default DESC, sort_order, metric_code
                LIMIT 1
                """;
        List<String> metrics = jdbcTemplate.query(sql, new MapSqlParameterSource("datasetCode", datasetCode), (rs, rowNum) -> rs.getString("metric_code"));
        return metrics.isEmpty() ? "METRIC_001" : metrics.get(0);
    }

    private Map<String, Object> countStoredRows(String datasetCode) {
        String sql = """
                SELECT
                    (SELECT COUNT(*)::bigint FROM public.sd_dashboard_area_observation WHERE dataset_code = :datasetCode) AS observation_count,
                    (SELECT COUNT(*)::bigint FROM public.sd_dashboard_geo_feature WHERE dataset_code = :datasetCode) AS feature_count,
                    (SELECT COUNT(*)::bigint FROM public.sd_dashboard_layer_catalog WHERE dataset_code = :datasetCode) AS layer_count
                """;
        return jdbcTemplate.queryForMap(sql, new MapSqlParameterSource("datasetCode", datasetCode));
    }

    private List<SourceSpec> resolveTargets(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return List.of(SourceSpec.values());
        }
        Optional<SourceSpec> source = SourceSpec.find(sourceCode);
        return source.map(List::of).orElseGet(List::of);
    }

    private JsonNode readJsonOrNull(String body) {
        try {
            String trimmed = body.trim();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return null;
            }
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<JsonNode> readXmlRows(String body) {
        List<JsonNode> rows = new ArrayList<>();
        try {
            String trimmed = body.trim();
            if (!trimmed.startsWith("<")) {
                return rows;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            NodeList items = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName("item");
            for (int index = 0; index < items.getLength(); index++) {
                Node item = items.item(index);
                if (item instanceof Element element) {
                    rows.add(xmlElementToJson(element));
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return rows;
    }

    private JsonNode xmlElementToJson(Element element) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) {
                objectNode.put(childElement.getTagName(), childElement.getTextContent());
            }
        }
        return objectNode;
    }

    private List<JsonNode> extractRows(JsonNode root) {
        List<JsonNode> rows = new ArrayList<>();
        for (String path : List.of(
                "response.body.items.item", "response.body.items", "Response.body.items.item", "Response.items.item",
                "body.items.item", "body.items", "items.item", "items", "item", "row", "records", "data")) {
            JsonNode node = path(root, path);
            addRows(node, rows);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        findRowsRecursive(root, rows);
        return rows;
    }

    private void findRowsRecursive(JsonNode node, List<JsonNode> rows) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if ("item".equalsIgnoreCase(key) || "row".equalsIgnoreCase(key) || "items".equalsIgnoreCase(key)) {
                    addRows(entry.getValue(), rows);
                } else if (rows.isEmpty()) {
                    findRowsRecursive(entry.getValue(), rows);
                }
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (child.isObject()) {
                    rows.add(child);
                }
            }
        }
    }

    private void addRows(JsonNode node, List<JsonNode> rows) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(rows::add);
        } else if (node.isObject()) {
            rows.add(node);
        }
    }

    private JsonNode path(JsonNode root, String dotPath) {
        JsonNode current = root;
        for (String segment : dotPath.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private boolean hasJsonApiError(JsonNode root) {
        return hasJsonApiErrorRecursive(root);
    }

    private boolean hasJsonApiErrorRecursive(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String text = value.asText("").trim();
                String upperText = text.toUpperCase();
                if (isResultCodeKey(key) && !text.isBlank() && !isSuccessResultCode(text)) {
                    return true;
                }
                if (isResultMessageKey(key)
                        && (upperText.contains("ERROR")
                            || upperText.contains("SERVICE KEY")
                            || upperText.contains("NO OPENAPI SERVICE"))) {
                    return true;
                }
                if (hasJsonApiErrorRecursive(value)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasJsonApiErrorRecursive(child)) {
                    return true;
                }
            }
            return false;
        }
        String text = node.asText("").toUpperCase();
        return text.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                || text.contains("SERVICE KEY IS NOT REGISTERED")
                || text.contains("NO OPENAPI SERVICE ERROR");
    }

    private boolean isResultCodeKey(String key) {
        return "resultCode".equalsIgnoreCase(key)
                || "returnReasonCode".equalsIgnoreCase(key)
                || "errCd".equalsIgnoreCase(key);
    }

    private boolean isResultMessageKey(String key) {
        return "resultMsg".equalsIgnoreCase(key)
                || "returnAuthMsg".equalsIgnoreCase(key)
                || "errMsg".equalsIgnoreCase(key);
    }

    private boolean isSuccessResultCode(String code) {
        String normalized = code.trim();
        return "0".equals(normalized)
                || "00".equals(normalized)
                || "0000".equals(normalized)
                || "NORMAL_CODE".equalsIgnoreCase(normalized)
                || "INFO-000".equalsIgnoreCase(normalized);
    }

    private boolean looksLikeApiError(String body) {
        String upper = body.toUpperCase();
        return upper.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                || upper.contains("SERVICE KEY IS NOT REGISTERED")
                || upper.contains("INVALID REQUEST PARAMETER ERROR")
                || upper.contains("<RETURNAUTHMSG>");
    }

    private String configuredValue(String name) {
        String value = directConfiguredValue(name);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if ("DATA_GO_KR_SERVICE_KEY".equals(name)) {
            String dataGoKey = directConfiguredValue("DATA_GO_KR_OPEN_API_KEY");
            if (!dataGoKey.isBlank()) {
                return dataGoKey;
            }
        }
        if ("DATA_GO_KR_OPEN_API_KEY".equals(name)) {
            String dataGoKey = directConfiguredValue("DATA_GO_KR_SERVICE_KEY");
            if (!dataGoKey.isBlank()) {
                return dataGoKey;
            }
        }
        if (("DATA_GO_KR_SERVICE_KEY".equals(name) || "DATA_GO_KR_OPEN_API_KEY".equals(name))) {
            String moisKey = directConfiguredValue("MOIS_OPEN_API_KEY");
            if (!moisKey.isBlank()) {
                return moisKey;
            }
        }
        return "";
    }

    private String directConfiguredValue(String name) {
        String value = environment.getProperty(name);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = readDotenvValue(name);
        return value == null ? "" : value.trim();
    }

    private String replaceTokens(String value, String statsYm, String keyword) {
        String today = LocalDate.now(SEOUL_ZONE).format(DATE_FORMAT);
        return value
                .replace("{statsYm}", statsYm)
                .replace("{today}", today)
                .replace("{keyword}", keyword);
    }

    private LocalDate baseDate(JsonNode row) {
        String date = firstText(row, "baseDate", "base_date", "fcstDate", "tm", "dataTime", "statsYm");
        if (date.matches("\\d{6}")) {
            return YearMonth.parse(date, STATS_YM_FORMAT).atEndOfMonth();
        }
        if (date.matches("\\d{8}")) {
            return LocalDate.parse(date, DATE_FORMAT);
        }
        return LocalDate.now(SEOUL_ZONE);
    }

    private String baseHour(JsonNode row) {
        String hour = firstText(row, "baseTime", "base_time", "fcstTime", "hour");
        if (hour.matches("\\d{4}")) {
            return hour.substring(0, 2);
        }
        if (hour.matches("\\d{1,2}")) {
            return hour.length() == 1 ? "0" + hour : hour;
        }
        return null;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String featureExternalId(JsonNode row, SourceSpec spec) {
        if (spec == SourceSpec.KECO_EV_CHARGER_MAIN) {
            String stationId = firstText(row, "statId", "statid");
            String chargerId = firstText(row, "chgerId");
            if (!stationId.isBlank() && !chargerId.isBlank()) {
                return stationId + ':' + chargerId;
            }
        }
        return firstText(row,
                "id", "ID", "contentid", "contentId", "bizesId", "statId", "statid", "chgerId", "csId",
                "mngNo", "manageNo", "afos_fid", "spot_cd", "fcltyCd", "facilityId", "시설ID", "관리번호");
    }

    private BigDecimal firstDecimal(JsonNode node, String... names) {
        for (String name : names) {
            BigDecimal value = decimal(node.path(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("").replace(",", "").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String readDotenvValue(String envName) {
        Path dotenv = Path.of(".env");
        if (!Files.isRegularFile(dotenv)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(dotenv)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.startsWith(envName + "=")) {
                    continue;
                }
                return stripQuotes(trimmed.substring((envName + "=").length()).trim());
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private Map<String, Object> result(SourceSpec spec, String status, int fetched, int saved, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceCode", spec.sourceCode());
        result.put("datasetCode", spec.datasetCode());
        result.put("status", status);
        result.put("storage", spec.storageTable());
        result.put("endpoint", spec.baseUrl() + spec.path());
        result.put("fetchedCount", fetched);
        result.put("savedCount", saved);
        result.put("message", message == null ? "" : message);
        return result;
    }

    private Map<String, Object> redactParams(Map<String, Object> params) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.toLowerCase().contains("key") || key.toLowerCase().contains("secret")) {
                redacted.put(key, "***REDACTED***");
            } else {
                redacted.put(key, value);
            }
        });
        return redacted;
    }

    private String json(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }

    private int normalizeNumOfRows(Integer numOfRows) {
        if (numOfRows == null || numOfRows < 1) {
            return DEFAULT_NUM_OF_ROWS;
        }
        return Math.min(numOfRows, MAX_NUM_OF_ROWS);
    }

    private String normalizeStatsYm(String statsYm) {
        if (statsYm == null || statsYm.isBlank()) {
            return YearMonth.now(SEOUL_ZONE).minusMonths(1).format(STATS_YM_FORMAT);
        }
        String normalized = statsYm.replace("-", "").trim();
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("statsYm은 YYYYMM 형식이어야 합니다.");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? "서울특별시" : keyword.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 1800) {
            return value;
        }
        return value.substring(0, 1800);
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private record SaveResult(int fetchedCount, int savedCount, String note) {
    }

    private record SidoArea(String areaCode, String sidoCode, String name, String fullName) {
    }

    private record RegionCount(SidoArea area, BigDecimal count, JsonNode rawPayload) {
    }

    private enum StorageType {
        OBSERVATION("sd_dashboard_area_observation"),
        FEATURE("sd_dashboard_geo_feature"),
        LAYER("sd_dashboard_layer_catalog");

        private final String table;

        StorageType(String table) {
            this.table = table;
        }
    }

    private record AuthParam(String paramName, String envName) {
    }

    private record SourceSpec(
            String sourceCode,
            String datasetCode,
            StorageType storageType,
            String defaultAreaLevel,
            String baseUrl,
            String path,
            List<AuthParam> authParams,
            Map<String, String> params,
            String blockerReason) {

        private static final SourceSpec AIRKOREA_AIR_QUALITY_MAIN = dataGo("AIRKOREA_AIR_QUALITY", "AIRKOREA_AIR_QUALITY_MAIN", StorageType.OBSERVATION, "SIGUNGU", "/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty", p("returnType", "json", "sidoName", "서울", "ver", "1.0"));
        private static final SourceSpec KECO_EV_CHARGER_MAIN = evCharger();
        private static final SourceSpec KMA_VILAGE_FCST_MAIN = dataGo("KMA_VILAGE_FCST", "KMA_VILAGE_FCST_MAIN", StorageType.OBSERVATION, null, "/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst", p("dataType", "JSON", "base_date", "{today}", "base_time", "0600", "nx", "60", "ny", "127"));
        private static final SourceSpec KOSIS_OPEN_API_MAIN = blocked("KOSIS_OPEN_API", "KOSIS_OPEN_API_MAIN", StorageType.OBSERVATION, null, "KOSIS 통계표 orgId/tblId/itmId 등 조회 조건과 KOSIS_OPEN_API_KEY가 필요합니다.");
        private static final SourceSpec MOIS_ADMM_AVG_AGE_MAIN = mois("MOIS_ADMM_AVG_AGE", "MOIS_ADMM_AVG_AGE_MAIN", "/1741000/admmAvgAge/selectAdmmAvgAge");
        private static final SourceSpec MOIS_ADMM_HSMB_HH_MAIN = mois("MOIS_ADMM_HSMB_HH", "MOIS_ADMM_HSMB_HH_MAIN", "/1741000/admmHsmbHh/selectAdmmHsmbHh");
        private static final SourceSpec MOIS_ADMM_POP_CHANGE_MAIN = mois("MOIS_ADMM_POP_CHANGE", "MOIS_ADMM_POP_CHANGE_MAIN", "/1741000/admmPopChange/selectAdmmPopChange");
        private static final SourceSpec MOIS_ADMM_PPLTN_HH_STUS_MAIN = mois("MOIS_ADMM_PPLTN_HH_STUS", "MOIS_ADMM_PPLTN_HH_STUS_MAIN", "/1741000/admmPpltnHhStus/selectAdmmPpltnHhStus");
        private static final SourceSpec MOIS_ADMM_SEXD_AGE_PPLTN_MAIN = new SourceSpec("MOIS_ADMM_SEXD_AGE_PPLTN", "MOIS_ADMM_SEXD_AGE_PPLTN_MAIN", StorageType.OBSERVATION, "EUPMYEONDONG", "https://apis.data.go.kr", "/1741000/admmSexdAgePpltn/selectAdmmSexdAgePpltn", List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), moisParams(), null);
        private static final SourceSpec SEMAS_STORE_INFO_MAIN = dataGo("SEMAS_STORE_INFO", "SEMAS_STORE_INFO_MAIN", StorageType.FEATURE, null, "/B553077/api/open/sdsc2/storeListInDong", p("type", "json", "divId", "adongCd", "key", "1168064000"));
        private static final SourceSpec SGIS_SOPEN_API_MAIN = blocked("SGIS_SOPEN_API", "SGIS_SOPEN_API_MAIN", StorageType.OBSERVATION, "SIGUNGU", "SGIS_CONSUMER_KEY/SGIS_CONSUMER_SECRET 토큰 발급과 세부 통계 operation 매핑이 필요합니다.");
        private static final SourceSpec VWORLD_WMS_WFS_MAIN = blocked("VWORLD_WMS_WFS", "VWORLD_WMS_WFS_MAIN", StorageType.LAYER, null, "VWORLD_API_KEY와 WFS typeName/layerName 확정이 필요합니다.");
        private static final SourceSpec KOROAD_ACCIDENT_HOTSPOT_MAIN = standard("KOROAD_ACCIDENT_HOTSPOT", "KOROAD_ACCIDENT_HOTSPOT_MAIN", StorageType.FEATURE, "SIGUNGU", "/openapi/tn_pubr_public_trfcacdnt_frqnt_spot_api", p("type", "json"));
        private static final SourceSpec KOROAD_PEDESTRIAN_HOTSPOT_MAIN = dataGo("KOROAD_PEDESTRIAN_HOTSPOT", "KOROAD_PEDESTRIAN_HOTSPOT_MAIN", StorageType.FEATURE, "SIGUNGU", "/B552061/frequentzoneOldman/getRestFrequentzoneOldman", p("type", "json", "searchYearCd", "2024", "siDo", "11", "guGun", ""));
        private static final SourceSpec MOIS_CASUALTY_CONCERN_AREA_MAIN = blocked("MOIS_CASUALTY_CONCERN_AREA", "MOIS_CASUALTY_CONCERN_AREA_MAIN", StorageType.OBSERVATION, "SIDO", "인명피해 우려지역은 파일/현황 데이터로 확인되며 OpenAPI operation path가 필요합니다.");
        private static final SourceSpec MOIS_CIVIL_DEFENSE_SHELTER_MAIN = blocked("MOIS_CIVIL_DEFENSE_SHELTER", "MOIS_CIVIL_DEFENSE_SHELTER_MAIN", StorageType.FEATURE, "SIGUNGU", "민방위대피시설 OpenAPI operation path 확인이 필요합니다.");
        private static final SourceSpec MOIS_COLLAPSE_HISTORY_MAIN = blocked("MOIS_COLLAPSE_HISTORY", "MOIS_COLLAPSE_HISTORY_MAIN", StorageType.FEATURE, "SIGUNGU", "붕괴이력 OpenAPI endpoint/응답 필드 확인이 필요합니다.");
        private static final SourceSpec MOIS_PUBLIC_RESTROOM_MAIN = blocked("MOIS_PUBLIC_RESTROOM", "MOIS_PUBLIC_RESTROOM_MAIN", StorageType.FEATURE, "SIGUNGU", "공중화장실 OpenAPI operation path 확인이 필요합니다.");
        private static final SourceSpec MOIS_SAFETY_INFO_MAIN = blocked("MOIS_SAFETY_INFO", "MOIS_SAFETY_INFO_MAIN", StorageType.OBSERVATION, "SIGUNGU", "안전정보 통합공개는 서비스 URL/필수 파라미터 확인이 필요합니다.");
        private static final SourceSpec MOIS_TSUNAMI_SHELTER_MAIN = dataGo("MOIS_TSUNAMI_SHELTER", "MOIS_TSUNAMI_SHELTER_MAIN", StorageType.FEATURE, "SIGUNGU", "/1741000/TsunamiShelter4/getTsunamiShelter4List", p("type", "json"));
        private static final SourceSpec MOLIT_BUS_STATION_STATUS_MAIN = blocked("MOLIT_BUS_STATION_STATUS", "MOLIT_BUS_STATION_STATUS_MAIN", StorageType.OBSERVATION, "SIGUNGU", "정류장 현황 API는 STCIS/LINK형 API로 apis.data.go.kr operation path 확인이 필요합니다.");
        private static final SourceSpec MOLIT_TRAFFIC_FORECAST_MAIN = blocked("MOLIT_TRAFFIC_FORECAST", "MOLIT_TRAFFIC_FORECAST_MAIN", StorageType.OBSERVATION, null, "MOLIT_TRAFFIC_FORECAST_API_URL/API_KEY 및 도로 링크 파라미터가 필요합니다.");
        private static final SourceSpec SAFE_DISASTER_ALERT_MAIN = dataGo("SAFE_DISASTER_ALERT", "SAFE_DISASTER_ALERT_MAIN", StorageType.OBSERVATION, "SIGUNGU", "/1741000/DisasterMsg3/getDisasterMsg1List", p("type", "json"));
        private static final SourceSpec STANDARD_AED_MAIN = standard("STANDARD_AED", "STANDARD_AED_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_automated_external_defibrillator_api", p("type", "json"));
        private static final SourceSpec STANDARD_BUS_STOP_MAIN = standard("STANDARD_BUS_STOP", "STANDARD_BUS_STOP_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_bus_sttn_api", p("type", "json"));
        private static final SourceSpec STANDARD_CHILD_PROTECTION_ZONE_MAIN = standard("STANDARD_CHILD_PROTECTION_ZONE", "STANDARD_CHILD_PROTECTION_ZONE_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_child_prtc_area_api", p("type", "json"));
        private static final SourceSpec STANDARD_LIBRARY_MAIN = standard("STANDARD_LIBRARY", "STANDARD_LIBRARY_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_lbrry_api", p("type", "json"));
        private static final SourceSpec STANDARD_PARKING_LOT_MAIN = standard("STANDARD_PARKING_LOT", "STANDARD_PARKING_LOT_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_prkplce_info_api", p("type", "json"));
        private static final SourceSpec STANDARD_SCHOOL_LOCATION_MAIN = standard("STANDARD_SCHOOL_LOCATION", "STANDARD_SCHOOL_LOCATION_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_elesch_mskul_lc_api", p("type", "json"));
        private static final SourceSpec STANDARD_TRAFFIC_CAMERA_MAIN = standard("STANDARD_TRAFFIC_CAMERA", "STANDARD_TRAFFIC_CAMERA_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_unmanned_traffic_camera_api", p("type", "json"));
        private static final SourceSpec STANDARD_URBAN_PARK_MAIN = standard("STANDARD_URBAN_PARK", "STANDARD_URBAN_PARK_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_cty_park_info_api", p("type", "json"));
        private static final SourceSpec ECOBANK_WMS_WFS_MAIN = blocked("ECOBANK_WMS_WFS", "ECOBANK_WMS_WFS_MAIN", StorageType.LAYER, null, "EcoBank WMS/WFS layerName과 인증/서비스 URL 확인이 필요합니다.");
        private static final SourceSpec HERITAGE_GIS_OPENAPI_MAIN = blocked("HERITAGE_GIS_OPENAPI", "HERITAGE_GIS_OPENAPI_MAIN", StorageType.LAYER, null, "국가유산공간정보 WMS/WFS layerName과 필수 파라미터 확인이 필요합니다.");
        private static final SourceSpec JUSO_SEARCH_API_MAIN = new SourceSpec("JUSO_SEARCH_API", "JUSO_SEARCH_API_MAIN", StorageType.FEATURE, "SIGUNGU", "https://business.juso.go.kr", "/addrlink/addrLinkApi.do", List.of(new AuthParam("confmKey", "JUSO_CONFIRM_KEY")), p("currentPage", "{pageNo}", "countPerPage", "{numOfRows}", "keyword", "{keyword}", "resultType", "json"), null);
        private static final SourceSpec KALIS_PUBLIC_FACILITY_INSPECTION_MAIN = dataGo("KALIS_PUBLIC_FACILITY_INSPECTION", "KALIS_PUBLIC_FACILITY_INSPECTION_MAIN", StorageType.FEATURE, null, "/B552016/PublicFacilDignService/getArDignList", p("type", "json"));
        private static final SourceSpec KTO_TOUR_API_MAIN = dataGo("KTO_TOUR_API", "KTO_TOUR_API_MAIN", StorageType.FEATURE, null, "/B551011/KorService2/areaBasedList2", p("MobileOS", "ETC", "MobileApp", "GisDataHub", "_type", "json", "arrange", "Q"));
        private static final SourceSpec KWATER_HYDRO_OPERATION_MAIN = dataGo("KWATER_HYDRO_OPERATION", "KWATER_HYDRO_OPERATION_MAIN", StorageType.OBSERVATION, null, "/B500001/dam/sluicePresentCondition/hourlist", p("_type", "json", "damcode", "2022510", "stdt", "{today}", "eddt", "{today}"));
        private static final SourceSpec MOLIT_ROAD_FACILITY_MAIN = new SourceSpec("MOLIT_ROAD_FACILITY", "MOLIT_ROAD_FACILITY_MAIN", StorageType.FEATURE, "SIGUNGU", "https://www.calspia.go.kr", "/io/openapi/fm/selectIoFmFctStsList.do", List.of(new AuthParam("serviceKey", "CALSPIA_OPEN_API_KEY")), p("type", "json"), null);
        private static final SourceSpec STANDARD_SOLAR_POWER_MAIN = standard("STANDARD_SOLAR_POWER", "STANDARD_SOLAR_POWER_MAIN", StorageType.FEATURE, "SIGUNGU", "/openapi/tn_pubr_public_solar_gen_flct_api", p("type", "json"));
        private static final SourceSpec STANDARD_TOURIST_SPOT_MAIN = standard("STANDARD_TOURIST_SPOT", "STANDARD_TOURIST_SPOT_MAIN", StorageType.FEATURE, null, "/openapi/tn_pubr_public_trrsrt_api", p("type", "json"));
        private static final SourceSpec MOIS_ADMM_BIRTH_REGISTERED_MAIN = mois("MOIS_ADMM_BIRTH_REGISTERED", "MOIS_ADMM_BIRTH_REGISTERED_MAIN", "/1741000/admmBirthRegist/selectAdmmBirthRegist");
        private static final SourceSpec MOIS_ADMM_DEATH_DEREGISTERED_MAIN = mois("MOIS_ADMM_DEATH_DEREGISTERED", "MOIS_ADMM_DEATH_DEREGISTERED_MAIN", "/1741000/admmSexdAgeDeathDeregist/selectAdmmSexdAgeDeathDeregist");

        private static final List<SourceSpec> ALL = List.of(
                AIRKOREA_AIR_QUALITY_MAIN,
                KECO_EV_CHARGER_MAIN,
                KMA_VILAGE_FCST_MAIN,
                KOSIS_OPEN_API_MAIN,
                MOIS_ADMM_AVG_AGE_MAIN,
                MOIS_ADMM_HSMB_HH_MAIN,
                MOIS_ADMM_POP_CHANGE_MAIN,
                MOIS_ADMM_PPLTN_HH_STUS_MAIN,
                MOIS_ADMM_SEXD_AGE_PPLTN_MAIN,
                SEMAS_STORE_INFO_MAIN,
                SGIS_SOPEN_API_MAIN,
                VWORLD_WMS_WFS_MAIN,
                KOROAD_ACCIDENT_HOTSPOT_MAIN,
                KOROAD_PEDESTRIAN_HOTSPOT_MAIN,
                MOIS_CASUALTY_CONCERN_AREA_MAIN,
                MOIS_CIVIL_DEFENSE_SHELTER_MAIN,
                MOIS_COLLAPSE_HISTORY_MAIN,
                MOIS_PUBLIC_RESTROOM_MAIN,
                MOIS_SAFETY_INFO_MAIN,
                MOIS_TSUNAMI_SHELTER_MAIN,
                MOLIT_BUS_STATION_STATUS_MAIN,
                MOLIT_TRAFFIC_FORECAST_MAIN,
                SAFE_DISASTER_ALERT_MAIN,
                STANDARD_AED_MAIN,
                STANDARD_BUS_STOP_MAIN,
                STANDARD_CHILD_PROTECTION_ZONE_MAIN,
                STANDARD_LIBRARY_MAIN,
                STANDARD_PARKING_LOT_MAIN,
                STANDARD_SCHOOL_LOCATION_MAIN,
                STANDARD_TRAFFIC_CAMERA_MAIN,
                STANDARD_URBAN_PARK_MAIN,
                ECOBANK_WMS_WFS_MAIN,
                HERITAGE_GIS_OPENAPI_MAIN,
                JUSO_SEARCH_API_MAIN,
                KALIS_PUBLIC_FACILITY_INSPECTION_MAIN,
                KTO_TOUR_API_MAIN,
                KWATER_HYDRO_OPERATION_MAIN,
                MOLIT_ROAD_FACILITY_MAIN,
                STANDARD_SOLAR_POWER_MAIN,
                STANDARD_TOURIST_SPOT_MAIN,
                MOIS_ADMM_BIRTH_REGISTERED_MAIN,
                MOIS_ADMM_DEATH_DEREGISTERED_MAIN);

        static SourceSpec[] values() {
            return ALL.toArray(SourceSpec[]::new);
        }

        static Optional<SourceSpec> find(String sourceCode) {
            return ALL.stream()
                    .filter(spec -> spec.sourceCode().equalsIgnoreCase(sourceCode) || spec.datasetCode().equalsIgnoreCase(sourceCode))
                    .findFirst();
        }

        String storageTable() {
            return storageType.table;
        }

        private static SourceSpec dataGo(String sourceCode, String datasetCode, StorageType storageType, String defaultAreaLevel, String path, Map<String, String> params) {
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "https://apis.data.go.kr", path, List.of(new AuthParam("serviceKey", "DATA_GO_KR_SERVICE_KEY")), params, null);
        }

        private static SourceSpec standard(String sourceCode, String datasetCode, StorageType storageType, String defaultAreaLevel, String path, Map<String, String> params) {
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "https://api.data.go.kr", path, List.of(new AuthParam("serviceKey", "DATA_GO_KR_SERVICE_KEY")), params, null);
        }

        private static SourceSpec evCharger() {
            return new SourceSpec(
                    "KECO_EV_CHARGER",
                    "KECO_EV_CHARGER_MAIN",
                    StorageType.FEATURE,
                    null,
                    "https://apis.data.go.kr/B552584/EvCharger",
                    "/getChargerInfo",
                    List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")),
                    p("dataType", "JSON"),
                    null);
        }

        private static SourceSpec mois(String sourceCode, String datasetCode, String path) {
            return new SourceSpec(sourceCode, datasetCode, StorageType.OBSERVATION, "EUPMYEONDONG", "https://apis.data.go.kr", path, List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), moisParams(), null);
        }

        private static SourceSpec blocked(String sourceCode, String datasetCode, StorageType storageType, String defaultAreaLevel, String reason) {
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "", "", List.of(), Map.of(), reason);
        }

        private static Map<String, String> moisParams() {
            return p("admmCd", "0000000000", "srchFrYm", "{statsYm}", "srchToYm", "{statsYm}", "lv", "1", "regSeCd", "1", "type", "JSON");
        }

        private static Map<String, String> p(String... values) {
            Map<String, String> params = new LinkedHashMap<>();
            for (int index = 0; index + 1 < values.length; index += 2) {
                params.put(values[index], values[index + 1]);
            }
            return params;
        }
    }
}
