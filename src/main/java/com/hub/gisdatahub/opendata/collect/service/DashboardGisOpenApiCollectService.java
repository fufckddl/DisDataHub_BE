package com.hub.gisdatahub.opendata.collect.service;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Statement;
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
    private static final DateTimeFormatter AIRKOREA_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter KMA_BASE_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter KMA_BASE_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_NUM_OF_ROWS = 5;
    private static final int MAX_NUM_OF_ROWS = 100;
    private static final int MAX_PAGE_GUARD = 1000;
    private static final String SEOUL_SIDO_AREA_CODE = "1100000000";
    private static final String SEOUL_SIDO_CODE = "11";
    private static final String EV_CHARGER_COUNT_METRIC_CODE = "EV_CHARGER_COUNT";
    private static final String EV_CHARGER_REGION_STATS_TYPE = "SIDO_DISTRIBUTION";
    private static final Duration EV_CHARGER_STATS_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration MOIS_DASHBOARD_CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration MOIS_DASHBOARD_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int EV_CHARGER_STATS_FAILURE_BREAK_COUNT = 3;
    private static final String MOIS_LEGAL_DONG_AREA_LEVEL = "LEGAL_DONG";

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
            String targetKeyword = usesAreaCollectionScope(spec) ? keyword : resolvedKeyword;
            results.add(collectOne(spec, resolvedPageNo, resolvedNumOfRows, resolvedStatsYm, targetKeyword));
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
    public Map<String, Object> cleanupMoisLegalDongObservations() {
        List<String> datasetCodes = moisLegalDongDatasetCodes();
        Map<String, Object> before = countMoisLegalDongObservationRows(datasetCodes);
        int deleted = deleteInvalidMoisLegalDongObservations(datasetCodes);
        int updated = normalizeMoisLegalDongObservations(datasetCodes);
        int catalogUpdated = normalizeMoisLegalDongDatasetCatalog(datasetCodes);
        Map<String, Object> after = countMoisLegalDongObservationRows(datasetCodes);

        return Map.of(
                "target", "MOIS_LEGAL_DONG_OBSERVATIONS",
                "datasetCodes", datasetCodes,
                "deletedInvalidRows", deleted,
                "updatedLegalRows", updated,
                "updatedCatalogRows", catalogUpdated,
                "before", before,
                "after", after);
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
        if (isMoisLegalDongSpec(spec)) {
            return collectMoisLegalDongs(spec, statsYm, keyword);
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            return collectAirKoreaSidoAirQuality(spec, keyword);
        }
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            return collectKmaSigunguWeather(spec, keyword);
        }
        if (spec.blockerReason() != null && !spec.blockerReason().isBlank()) {
            long runId = startRun(spec, requestParams(spec, pageNo, numOfRows, statsYm, keyword));
            finishRun(runId, "SKIPPED", 0, 0, 1, spec.blockerReason());
            return result(spec, "SKIPPED", 0, 0, spec.blockerReason());
        }
        if (shouldCollectAllFeaturePages(spec)) {
            return collectAllFeaturePages(spec, pageNo, statsYm, keyword);
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

    private Map<String, Object> collectAllFeaturePages(SourceSpec spec, int startPageNo, String statsYm, String keyword) {
        int pageNo = Math.max(startPageNo, DEFAULT_PAGE_NO);
        int numOfRows = MAX_NUM_OF_ROWS;
        Map<String, Object> initialParams = requestParams(spec, pageNo, numOfRows, statsYm, keyword);
        initialParams.put("collectionMode", "ALL_PAGES");
        String missingKey = ensureAuthParams(spec, initialParams);
        if (missingKey != null) {
            long runId = startRun(spec, initialParams);
            String blocker = "missing API key: " + missingKey;
            finishRun(runId, "SKIPPED", 0, 0, 1, blocker);
            return result(spec, "SKIPPED", 0, 0, blocker);
        }

        long runId = startRun(spec, initialParams);
        int fetched = 0;
        int saved = 0;
        int failed = 0;
        String lastError = null;
        boolean hasNextPage = true;
        int lastPageNo = pageNo - 1;

        while (hasNextPage && pageNo < startPageNo + MAX_PAGE_GUARD) {
            Map<String, Object> queryParams = requestParams(spec, pageNo, numOfRows, statsYm, keyword);
            try {
                String body = dataCollectClient.callOpenApi(spec.baseUrl(), spec.path(), queryParams);
                SaveResult saveResult = saveResponse(runId, spec, body);
                fetched += saveResult.fetchedCount();
                saved += saveResult.savedCount();
                lastPageNo = pageNo;
                hasNextPage = hasNextPage(body, pageNo, numOfRows, saveResult);
                pageNo++;
            } catch (Exception exception) {
                failed++;
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                hasNextPage = false;
            }
        }

        if (hasNextPage) {
            failed++;
            lastError = "page guard exceeded: " + MAX_PAGE_GUARD;
        }

        String runStatus = saved > 0 ? (failed > 0 ? "PARTIAL" : "SUCCEEDED") : failed > 0 ? "FAILED" : "SKIPPED";
        String status = saved > 0 ? (failed > 0 ? "PARTIAL" : "COMPLETED") : failed > 0 ? "FAILED" : "NO_DATA";
        finishRun(runId, runStatus, fetched, saved, failed, lastError);
        return result(spec, status, fetched, saved,
                lastError == null
                        ? "all feature pages collected: " + startPageNo + "-" + lastPageNo
                        : lastError);
    }

    private boolean shouldCollectAllFeaturePages(SourceSpec spec) {
        return spec.storageType() == StorageType.FEATURE
                && spec != SourceSpec.KECO_EV_CHARGER_MAIN
                && spec != SourceSpec.JUSO_SEARCH_API_MAIN;
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

    private Map<String, Object> collectMoisLegalDongs(SourceSpec spec, String statsYm, String keyword) {
        Map<String, Object> sampleParams = requestParams(spec, 1, MAX_NUM_OF_ROWS, statsYm, normalizeKeyword(null));
        String missingKey = ensureAuthParams(spec, sampleParams);
        if (missingKey != null) {
            return result(spec, "FAILED", 0, 0, "missing API key: " + missingKey);
        }

        List<SigunguArea> areas = findSigunguAreas(resolveMoisLegalDongScope(keyword));
        int deletedExistingRows = deleteDashboardObservationsForStatsMonth(spec, statsYm, areas);
        long runId = startRun(spec, Map.of(
                "mode", "SIGUNGU_TO_LEGAL_DONG",
                "statsYm", statsYm,
                "numOfRows", MAX_NUM_OF_ROWS,
                "deletedExistingRows", deletedExistingRows,
                "sampleParams", redactParams(sampleParams)));
        int fetched = 0;
        int saved = 0;
        int failed = 0;
        String lastError = null;

        try {
            for (SigunguArea area : areas) {
                int pageNo = 1;
                boolean hasNextPage = true;
                while (hasNextPage) {
                    Map<String, Object> queryParams = requestParams(spec, pageNo, MAX_NUM_OF_ROWS, statsYm, normalizeKeyword(null));
                    ensureAuthParams(spec, queryParams);
                    queryParams.put("stdgCd", area.areaCode());
                    queryParams.put("lv", "3");

                    try {
                        String body = dataCollectClient.callOpenApi(
                                spec.baseUrl(),
                                spec.path(),
                                queryParams,
                                MOIS_DASHBOARD_CONNECT_TIMEOUT,
                                MOIS_DASHBOARD_READ_TIMEOUT);
                        SaveResult saveResult = saveResponse(runId, spec, body);
                        fetched += saveResult.fetchedCount();
                        saved += saveResult.savedCount();
                        hasNextPage = hasNextPage(body, pageNo, MAX_NUM_OF_ROWS, saveResult);
                        pageNo++;
                    } catch (Exception exception) {
                        failed++;
                        hasNextPage = false;
                        lastError = area.areaCode() + " " + area.name() + ": "
                                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                        if (failed >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT) {
                            break;
                        }
                    }
                }
                if (failed >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT) {
                    break;
                }
            }

            boolean hardFailed = failed >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT && saved == 0;
            String runStatus = saved > 0 ? "SUCCEEDED" : hardFailed ? "FAILED" : "SKIPPED";
            String status = saved > 0 ? "COMPLETED" : hardFailed ? "FAILED" : "NO_DATA";
            finishRun(runId, runStatus, fetched, saved, failed, lastError);
            return result(spec, status, fetched, saved,
                    lastError == null ? "sigungu legal-dong rows collected" : lastError);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            finishRun(runId, "FAILED", fetched, saved, failed + 1, message);
            return result(spec, "FAILED", fetched, saved, message);
        }
    }

    private Map<String, Object> collectAirKoreaSidoAirQuality(SourceSpec spec, String keyword) {
        MoisAreaScope scope = resolveAreaCollectionScope(keyword);
        List<SidoArea> areas = findSidoAreas(scope);
        if (areas.isEmpty()) {
            return result(spec, "NO_DATA", 0, 0, "수집 대상 시도 코드가 없습니다.");
        }

        Map<String, Object> sampleParams = requestParams(spec, 1, MAX_NUM_OF_ROWS, normalizeStatsYm(null), normalizeKeyword(null));
        String missingKey = ensureAuthParams(spec, sampleParams);
        if (missingKey != null) {
            return result(spec, "FAILED", 0, 0, "missing API key: " + missingKey);
        }

        int deletedExistingRows = deleteDashboardObservationsForScope(spec, scope);
        long runId = startRun(spec, Map.of(
                "mode", "SIDO_AIR_QUALITY",
                "areaCount", areas.size(),
                "deletedExistingRows", deletedExistingRows,
                "sampleParams", redactParams(sampleParams)));
        int fetched = 0;
        int saved = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        String lastError = null;

        for (SidoArea area : areas) {
            Map<String, Object> queryParams = requestParams(spec, 1, MAX_NUM_OF_ROWS, normalizeStatsYm(null), normalizeKeyword(null));
            ensureAuthParams(spec, queryParams);
            queryParams.put("sidoName", airKoreaSidoName(area));

            try {
                String body = dataCollectClient.callOpenApi(
                        spec.baseUrl(),
                        spec.path(),
                        queryParams,
                        MOIS_DASHBOARD_CONNECT_TIMEOUT,
                        MOIS_DASHBOARD_READ_TIMEOUT);
                SaveResult saveResult = saveObservationResponseWithContext(runId, spec, body, Map.of(
                        "sidoCode", area.sidoCode(),
                        "sidoAreaCode", area.areaCode(),
                        "sidoName", area.name(),
                        "sidoFullName", area.fullName(),
                        "airKoreaSidoName", airKoreaSidoName(area)));
                fetched += saveResult.fetchedCount();
                saved += saveResult.savedCount();
                consecutiveFailures = 0;
            } catch (Exception exception) {
                failed++;
                consecutiveFailures++;
                lastError = area.areaCode() + " " + area.name() + ": "
                        + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                if (consecutiveFailures >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT) {
                    break;
                }
            }
        }

        String runStatus = saved > 0 ? (failed > 0 ? "PARTIAL" : "SUCCEEDED") : failed > 0 ? "FAILED" : "SKIPPED";
        String status = saved > 0 ? (failed > 0 ? "PARTIAL" : "COMPLETED") : failed > 0 ? "FAILED" : "NO_DATA";
        finishRun(runId, runStatus, fetched, saved, failed, lastError);
        return result(spec, status, fetched, saved,
                lastError == null ? "sido air quality rows collected" : lastError);
    }

    private Map<String, Object> collectKmaSigunguWeather(SourceSpec spec, String keyword) {
        MoisAreaScope scope = resolveAreaCollectionScope(keyword);
        List<WeatherGridArea> areas = findWeatherGridAreas(scope);
        if (areas.isEmpty()) {
            return result(spec, "NO_DATA", 0, 0, "수집 대상 시군구 중심좌표가 없습니다.");
        }

        Map<String, Object> sampleParams = requestParams(spec, 1, MAX_NUM_OF_ROWS, normalizeStatsYm(null), normalizeKeyword(null));
        String missingKey = ensureAuthParams(spec, sampleParams);
        if (missingKey != null) {
            return result(spec, "FAILED", 0, 0, "missing API key: " + missingKey);
        }

        int deletedExistingRows = deleteDashboardObservationsForScope(spec, scope);
        long runId = startRun(spec, Map.of(
                "mode", "SIGUNGU_KMA_GRID",
                "areaCount", areas.size(),
                "deletedExistingRows", deletedExistingRows,
                "sampleParams", redactParams(sampleParams)));
        Map<String, String> responseByGrid = new LinkedHashMap<>();
        int fetched = 0;
        int saved = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        String lastError = null;

        for (WeatherGridArea area : areas) {
            String gridKey = area.gridX() + ":" + area.gridY();
            try {
                String body = responseByGrid.get(gridKey);
                if (body == null) {
                    Map<String, Object> queryParams = requestParams(spec, 1, MAX_NUM_OF_ROWS, normalizeStatsYm(null), normalizeKeyword(null));
                    ensureAuthParams(spec, queryParams);
                    queryParams.put("nx", area.gridX());
                    queryParams.put("ny", area.gridY());
                    body = dataCollectClient.callOpenApi(
                            spec.baseUrl(),
                            spec.path(),
                            queryParams,
                            MOIS_DASHBOARD_CONNECT_TIMEOUT,
                            MOIS_DASHBOARD_READ_TIMEOUT);
                    responseByGrid.put(gridKey, body);
                }
                SaveResult saveResult = saveObservationResponseWithContext(runId, spec, body, Map.of(
                        "areaCode", area.areaCode(),
                        "areaLevel", area.areaLevel(),
                        "areaName", area.name(),
                        "areaFullName", area.fullName(),
                        "nx", String.valueOf(area.gridX()),
                        "ny", String.valueOf(area.gridY()),
                        "longitude", String.valueOf(area.longitude()),
                        "latitude", String.valueOf(area.latitude())));
                fetched += saveResult.fetchedCount();
                saved += saveResult.savedCount();
                consecutiveFailures = 0;
            } catch (Exception exception) {
                failed++;
                consecutiveFailures++;
                lastError = area.areaCode() + " " + area.name() + ": "
                        + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                if (consecutiveFailures >= EV_CHARGER_STATS_FAILURE_BREAK_COUNT) {
                    break;
                }
            }
        }

        String runStatus = saved > 0 ? (failed > 0 ? "PARTIAL" : "SUCCEEDED") : failed > 0 ? "FAILED" : "SKIPPED";
        String status = saved > 0 ? (failed > 0 ? "PARTIAL" : "COMPLETED") : failed > 0 ? "FAILED" : "NO_DATA";
        finishRun(runId, runStatus, fetched, saved, failed, lastError);
        return result(spec, status, fetched, saved,
                lastError == null ? "sigungu KMA grid rows collected" : lastError);
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
        return findSidoAreas(new MoisAreaScope(null, null));
    }

    private List<SidoArea> findSidoAreas(MoisAreaScope scope) {
        String sidoCode = blankToNull(scope.sidoCode());
        String sigunguCode = blankToNull(scope.sigunguCode());
        if (sidoCode == null && sigunguCode != null && sigunguCode.length() >= 2) {
            sidoCode = sigunguCode.substring(0, 2);
        }
        String areaFilter = "";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (sidoCode != null) {
            areaFilter = " AND sido_code = :sidoCode\n";
            params.addValue("sidoCode", sidoCode);
        }
        String sql = """
                SELECT area_code, sido_code, name, full_name
                FROM public.sd_area_code
                WHERE level = 'SIDO'
                  AND is_active = TRUE
                  AND sido_code IS NOT NULL
                  %s
                ORDER BY sido_code
                """.formatted(areaFilter);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new SidoArea(
                rs.getString("area_code"),
                rs.getString("sido_code"),
                rs.getString("name"),
                rs.getString("full_name")));
    }

    private List<SigunguArea> findSigunguAreas(MoisAreaScope scope) {
        String sidoCode = blankToNull(scope.sidoCode());
        String sigunguCode = blankToNull(scope.sigunguCode());
        String areaFilter = "";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (sidoCode != null) {
            areaFilter += " AND sido_code = :sidoCode\n";
            params.addValue("sidoCode", sidoCode);
        }
        if (sigunguCode != null) {
            areaFilter += " AND sigungu_code = :sigunguCode\n";
            params.addValue("sigunguCode", sigunguCode);
        }
        String sql = """
                SELECT area_code, sido_code, sigungu_code, name, full_name
                FROM public.sd_area_code
                WHERE level = 'SIGUNGU'
                  AND is_active = TRUE
                  AND sigungu_code IS NOT NULL
                  %s
                ORDER BY sido_code, sigungu_code
                """.formatted(areaFilter);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new SigunguArea(
                rs.getString("area_code"),
                rs.getString("sido_code"),
                rs.getString("sigungu_code"),
                rs.getString("name"),
                rs.getString("full_name")));
    }

    private List<WeatherGridArea> findWeatherGridAreas(MoisAreaScope scope) {
        String sidoCode = blankToNull(scope.sidoCode());
        String sigunguCode = blankToNull(scope.sigunguCode());
        String areaFilter = "";
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (sidoCode != null) {
            areaFilter += " AND c.sido_code = :sidoCode\n";
            params.addValue("sidoCode", sidoCode);
        }
        if (sigunguCode != null) {
            areaFilter += " AND c.sigungu_code = :sigunguCode\n";
            params.addValue("sigunguCode", sigunguCode);
        }
        String sql = """
                SELECT
                    c.area_code,
                    c.level,
                    c.name,
                    c.full_name,
                    ST_X(ST_Transform(b.center, 4326)) AS longitude,
                    ST_Y(ST_Transform(b.center, 4326)) AS latitude
                FROM public.sd_area_code c
                JOIN public.sd_area_boundary b
                    ON b.area_code = c.area_code
                WHERE c.level = 'SIGUNGU'
                  AND c.is_active = TRUE
                  AND c.sigungu_code IS NOT NULL
                  AND b.center IS NOT NULL
                  %s
                ORDER BY c.sido_code, c.sigungu_code
                """.formatted(areaFilter);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            double longitude = rs.getDouble("longitude");
            double latitude = rs.getDouble("latitude");
            KmaGrid grid = toKmaGrid(longitude, latitude);
            return new WeatherGridArea(
                    rs.getString("area_code"),
                    rs.getString("level"),
                    rs.getString("name"),
                    rs.getString("full_name"),
                    longitude,
                    latitude,
                    grid.x(),
                    grid.y());
        });
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
        for (String path : List.of("totalCount", "response.body.totalCount", "body.totalCount", "Response.head.totalCount", "head.totalCount")) {
            BigDecimal value = decimal(path(root, path));
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private boolean hasNextPage(String body, int pageNo, int numOfRows, SaveResult saveResult) {
        JsonNode root = readJsonOrNull(body);
        if (root != null) {
            Optional<BigDecimal> totalCount = extractTotalCount(root);
            if (totalCount.isPresent()) {
                return pageNo * numOfRows < totalCount.get().intValue();
            }
        }
        return saveResult.fetchedCount() >= numOfRows;
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
                List<JsonNode> observationRows = new ArrayList<>();
                int saved = 0;
                for (JsonNode row : xmlRows) {
                    if (shouldSkipObservation(spec, row)) {
                        continue;
                    }
                    if (spec.storageType() == StorageType.FEATURE) {
                        saved += insertFeature(runId, spec, row);
                    } else {
                        observationRows.add(row);
                    }
                }
                if (spec.storageType() != StorageType.FEATURE) {
                    saved += insertObservations(runId, spec, observationRows);
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
            if (shouldSkipObservation(spec, root)) {
                return new SaveResult(1, 0, "root response skipped");
            }
            saved = spec.storageType() == StorageType.FEATURE
                    ? insertMetadataFeature(runId, spec, root.toString())
                    : insertObservation(runId, spec, root);
            return new SaveResult(1, saved, "root response saved");
        }

        List<JsonNode> observationRows = new ArrayList<>();
        for (JsonNode row : rows) {
            if (shouldSkipObservation(spec, row)) {
                continue;
            }
            if (spec.storageType() == StorageType.FEATURE) {
                saved += insertFeature(runId, spec, row);
            } else {
                observationRows.add(row);
            }
        }
        if (spec.storageType() != StorageType.FEATURE) {
            saved += insertObservations(runId, spec, observationRows);
        }
        return new SaveResult(rows.size(), saved, "parsed rows saved");
    }

    private SaveResult saveObservationResponseWithContext(
            long runId,
            SourceSpec spec,
            String body,
            Map<String, String> context) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            return new SaveResult(0, 0, "empty response");
        }
        if (looksLikeApiError(body)) {
            throw new IllegalStateException(abbreviate(body));
        }

        JsonNode root = readJsonOrNull(body);
        if (root == null) {
            List<JsonNode> xmlRows = readXmlRows(body);
            List<JsonNode> observationRows = new ArrayList<>();
            int saved = 0;
            for (JsonNode row : xmlRows) {
                JsonNode enrichedRow = enrichRow(row, context);
                if (shouldSkipObservation(spec, enrichedRow)) {
                    continue;
                }
                observationRows.add(enrichedRow);
            }
            saved += insertObservations(runId, spec, observationRows);
            return new SaveResult(xmlRows.size(), saved, "parsed xml rows saved with area context");
        }
        if (hasJsonApiError(root)) {
            throw new IllegalStateException(abbreviate(root.toString()));
        }

        List<JsonNode> rows = extractRows(root);
        if (rows.isEmpty()) {
            JsonNode enrichedRoot = enrichRow(root, context);
            if (shouldSkipObservation(spec, enrichedRoot)) {
                return new SaveResult(1, 0, "root response skipped");
            }
            return new SaveResult(1, insertObservation(runId, spec, enrichedRoot), "root response saved with area context");
        }

        int saved = 0;
        List<JsonNode> observationRows = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode enrichedRow = enrichRow(row, context);
            if (shouldSkipObservation(spec, enrichedRow)) {
                continue;
            }
            observationRows.add(enrichedRow);
        }
        saved += insertObservations(runId, spec, observationRows);
        return new SaveResult(rows.size(), saved, "parsed rows saved with area context");
    }

    private JsonNode enrichRow(JsonNode row, Map<String, String> context) {
        ObjectNode enriched;
        if (row != null && row.isObject()) {
            enriched = (ObjectNode) row.deepCopy();
        } else {
            enriched = objectMapper.createObjectNode();
            enriched.set("value", row == null ? objectMapper.nullNode() : row);
        }
        context.forEach(enriched::put);
        return enriched;
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
        if (longitude == null || latitude == null) {
            return 0;
        }
        String externalId = featureExternalId(row, spec);
        if (externalId.isBlank()) {
            externalId = spec.datasetCode() + ':' + hash(row.toString());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("metricCode", metricCode(spec.datasetCode()))
                .addValue("runId", runId)
                .addValue("externalId", externalId)
                .addValue("featureName", blankToNull(firstText(row, "name", "title", "featureName", "bizesNm", "statNm", "csNm", "fcltyNm", "시설명", "상호명", "관광지명", "schoolNm", "LBRRY_NM", "lbrryNm", "prkplceNm", "nodenm")))
                .addValue("featureCategory", blankToNull(firstText(row, "category", "contenttypeid", "indsSclsNm", "chgerType", "busiNm", "fcltyType", "type", "구분", "lbrrySe")))
                .addValue("sourceAreaCode", blankToNull(firstText(row, "areaCode", "areacode", "sigungucode", "ctprvnCd", "signguCd", "adongCd", "bjd_cd", "zscode", "zcode")))
                .addValue("sourceAreaName", blankToNull(firstText(row, "areaName", "addr1", "ctprvnNm", "signguNm", "sido_sgg_nm", "institutionNm", "instt_nm", "insttNm", "zcodeNm", "zscodeNm")))
                .addValue("address", blankToNull(firstText(row, "addr", "addr1", "address", "lnmadr", "lnoAdr", "LCTN_LOTNO_ADDR", "소재지주소")))
                .addValue("roadAddress", blankToNull(firstText(row, "roadAddress", "rdnmadr", "rdnmAdr", "LCTN_ROAD_NM_ADDR", "도로명주소")))
                .addValue("longitude", longitude)
                .addValue("latitude", latitude)
                .addValue("sourceCrs", "EPSG:4326")
                .addValue("numericValue", firstDecimal(row, "value", "count", "cnt", "CAPA", "ar", "parkingchrgeInfo", "seatCo", "bookCo"))
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
        MapSqlParameterSource params = observationParams(runId, spec, row, metricCode(spec.datasetCode()));
        deleteExistingObservation(params);
        return jdbcTemplate.update(observationInsertSql(), params);
    }

    private int insertObservations(long runId, SourceSpec spec, List<JsonNode> rows) throws JsonProcessingException {
        if (rows.isEmpty()) {
            return 0;
        }
        String metricCode = metricCode(spec.datasetCode());
        MapSqlParameterSource[] params = new MapSqlParameterSource[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            params[index] = observationParams(runId, spec, rows.get(index), metricCode);
        }
        deleteExistingObservations(params);
        int[] counts = jdbcTemplate.batchUpdate(observationInsertSql(), params);
        int saved = 0;
        for (int count : counts) {
            if (count > 0) {
                saved += count;
            } else if (count == Statement.SUCCESS_NO_INFO) {
                saved++;
            }
        }
        return saved;
    }

    private String observationInsertSql() {
        return """
                INSERT INTO public.sd_dashboard_area_observation (
                    dataset_code, metric_code, collection_run_id, area_code, area_level, source_area_code,
                    source_area_name, grid_x, grid_y, base_date, base_hour, observed_at, numeric_value,
                    text_value, json_value, unit, dimensions, raw_payload, created_at, updated_at
                ) VALUES (
                    :datasetCode, :metricCode, :runId, :areaCode, :areaLevel, :sourceAreaCode, :sourceAreaName,
                    :gridX, :gridY, :baseDate, :baseHour, :observedAt, :numericValue, :textValue,
                    CAST(:jsonValue AS jsonb), :unit, CAST(:dimensions AS jsonb), CAST(:rawPayload AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING
                """;
    }

    private MapSqlParameterSource observationParams(long runId, SourceSpec spec, JsonNode row, String metricCode)
            throws JsonProcessingException {
        BigDecimal numericValue = observationNumericValue(spec, row);
        String sourceAreaCode = observationSourceAreaCode(spec, row);
        String sourceAreaName = observationSourceAreaName(spec, row);
        StoredArea storedArea = resolveStoredArea(spec, sourceAreaCode, sourceAreaName, row);
        String fallbackAreaLevel = observationAreaLevel(spec, sourceAreaCode);
        String areaLevel = isMoisLegalDongSpec(spec)
                ? fallbackAreaLevel
                : storedArea.level() != null ? storedArea.level() : fallbackAreaLevel;
        return new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("metricCode", metricCode)
                .addValue("runId", runId)
                .addValue("areaCode", storedArea.areaCode())
                .addValue("areaLevel", blankToNull(areaLevel))
                .addValue("sourceAreaCode", blankToNull(sourceAreaCode))
                .addValue("sourceAreaName", blankToNull(sourceAreaName))
                .addValue("gridX", blankToNull(firstText(row, "nx", "gridX")))
                .addValue("gridY", blankToNull(firstText(row, "ny", "gridY")))
                .addValue("baseDate", baseDate(row))
                .addValue("baseHour", baseHour(row))
                .addValue("observedAt", observedAt(row))
                .addValue("numericValue", numericValue)
                .addValue("textValue", numericValue == null ? abbreviate(row.toString()) : null)
                .addValue("jsonValue", row.toString())
                .addValue("unit", blankToNull(observationUnit(spec, row)))
                .addValue("dimensions", json(observationDimensions(spec, row)))
                .addValue("rawPayload", row.toString());
    }

    private BigDecimal observationNumericValue(SourceSpec spec, JsonNode row) {
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            return firstDecimal(row, "obsrValue", "fcstValue", "value");
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            return firstDecimal(row, "pm10Value", "pm25Value", "khaiValue", "so2Value", "coValue", "o3Value", "no2Value");
        }
        if (spec == SourceSpec.MOIS_ADMM_AVG_AGE_MAIN) {
            return firstDecimal(row, "avgAge", "avrgAge", "totAvrgAge", "totAvgAge", "maleAvrgAge", "femlAvrgAge", "meanAge", "age", "value", "val", "data");
        }
        if (spec == SourceSpec.MOIS_ADMM_HSMB_HH_MAIN) {
            return firstDecimal(row, "hhCnt", "hshldCnt", "hshldCo", "householdCount", "totHhCnt", "onePrsnHhCnt", "cnt", "count", "value");
        }
        if (spec == SourceSpec.MOIS_ADMM_POP_CHANGE_MAIN) {
            return firstDecimal(row, "popChange", "ppltnChange", "totNmprIncre", "maleNmprIncre", "femlNmprIncre", "incDec", "incDecCnt", "increaseDecrease", "chgPopulation", "totPpltn", "timtNmprCnt", "lsmtNmprCnt", "population", "cnt", "count", "value");
        }
        return firstDecimal(row, "value", "val", "data", "cnt", "count", "totalCount", "totNmprCnt", "totPpltn", "population", "avgAge", "hhCnt", "numOfRows");
    }

    private String observationSourceAreaCode(SourceSpec spec, JsonNode row) {
        if (isMoisLegalDongSpec(spec)) {
            return firstText(row, "stdgCd");
        }
        String sourceAreaCode = firstText(row, "areaCode", "stdgCd", "admmCd", "admCd", "ctpvCd", "sggCd", "dongCd", "법정동코드");
        if (!sourceAreaCode.isBlank()) {
            return sourceAreaCode;
        }
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            String gridX = firstText(row, "nx", "gridX");
            String gridY = firstText(row, "ny", "gridY");
            if (!gridX.isBlank() && !gridY.isBlank()) {
                return "KMA:" + gridX + ':' + gridY;
            }
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            String station = firstText(row, "stationCode", "stationName", "망", "측정소명");
            if (!station.isBlank()) {
                return "AIR:" + station;
            }
        }
        return "";
    }

    private String observationSourceAreaName(SourceSpec spec, JsonNode row) {
        if (isMoisLegalDongSpec(spec)) {
            return firstText(row, "stdgNm");
        }
        String sourceAreaName = firstText(row, "stdgNm", "liNm", "dongNm", "emdNm", "stationName", "stnNm", "areaName", "sggNm", "ctpvNm", "sidoName", "addr");
        if (!sourceAreaName.isBlank()) {
            return sourceAreaName;
        }
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            String gridX = firstText(row, "nx", "gridX");
            String gridY = firstText(row, "ny", "gridY");
            if (!gridX.isBlank() && !gridY.isBlank()) {
                return "기상 격자 " + gridX + '/' + gridY;
            }
        }
        return "";
    }

    private String observationAreaLevel(SourceSpec spec, String sourceAreaCode) {
        if (isMoisLegalDongSpec(spec)) {
            return MOIS_LEGAL_DONG_AREA_LEVEL;
        }
        if (spec.defaultAreaLevel() != null && !spec.defaultAreaLevel().isBlank()) {
            return spec.defaultAreaLevel();
        }
        if (sourceAreaCode != null && sourceAreaCode.startsWith("KMA:")) {
            return "GRID";
        }
        return null;
    }

    private boolean shouldSkipObservation(SourceSpec spec, JsonNode row) {
        return spec.storageType() == StorageType.OBSERVATION
                && isMoisLegalDongSpec(spec)
                && !isMoisLegalDongObservationRow(row);
    }

    private boolean isMoisLegalDongObservationRow(JsonNode row) {
        return isLegalDongAreaCode(firstText(row, "stdgCd"));
    }

    private boolean isLegalDongAreaCode(String areaCode) {
        return areaCode != null
                && areaCode.matches("\\d{10}")
                && !areaCode.substring(5).equals("00000");
    }

    private String observationUnit(SourceSpec spec, JsonNode row) {
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            return switch (firstText(row, "category")) {
                case "T1H" -> "℃";
                case "RN1" -> "mm";
                case "UUU", "VVV", "WSD" -> "m/s";
                case "REH" -> "%";
                case "VEC" -> "deg";
                default -> null;
            };
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            return "㎍/㎥";
        }
        if (spec == SourceSpec.MOIS_ADMM_AVG_AGE_MAIN) {
            return "세";
        }
        if (spec == SourceSpec.MOIS_ADMM_HSMB_HH_MAIN) {
            return "세대";
        }
        if (spec == SourceSpec.MOIS_ADMM_POP_CHANGE_MAIN) {
            return "명";
        }
        return null;
    }

    private Map<String, Object> observationDimensions(SourceSpec spec, JsonNode row) {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("collector", "dashboard-gis");
        dimensions.put("sourceCode", spec.sourceCode());
        putIfNotBlank(dimensions, "category", firstText(row, "category"));
        putIfNotBlank(dimensions, "metricLabel", observationMetricLabel(spec, row));
        putIfNotBlank(dimensions, "sidoName", firstText(row, "sidoName", "ctpvNm"));
        putIfNotBlank(dimensions, "sigunguName", firstText(row, "sggNm"));
        putIfNotBlank(dimensions, "legalDongCode", firstText(row, "stdgCd"));
        putIfNotBlank(dimensions, "legalDongName", firstText(row, "stdgNm"));
        putIfNotBlank(dimensions, "administrativeDongCode", firstText(row, "admmCd"));
        putIfNotBlank(dimensions, "administrativeDongName", firstText(row, "dongNm"));
        putIfNotBlank(dimensions, "tong", firstText(row, "tong"));
        putIfNotBlank(dimensions, "ban", firstText(row, "ban"));
        putIfNotBlank(dimensions, "stationName", firstText(row, "stationName", "stnNm"));
        putIfNotBlank(dimensions, "statsYm", firstText(row, "statsYm", "srchYm"));
        return dimensions;
    }

    private String observationMetricLabel(SourceSpec spec, JsonNode row) {
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            return switch (firstText(row, "category")) {
                case "T1H" -> "기온";
                case "RN1" -> "1시간 강수량";
                case "UUU" -> "동서바람성분";
                case "VVV" -> "남북바람성분";
                case "REH" -> "습도";
                case "PTY" -> "강수형태";
                case "VEC" -> "풍향";
                case "WSD" -> "풍속";
                default -> firstText(row, "category");
            };
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            return "PM10";
        }
        return "";
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private StoredArea resolveStoredArea(SourceSpec spec, String sourceAreaCode, String sourceAreaName, JsonNode row) {
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            String areaCode = firstText(row, "areaCode");
            if (!areaCode.isBlank()) {
                return new StoredArea(areaCode, blankToNull(firstText(row, "areaLevel")));
            }
        }
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN && sourceAreaCode != null && sourceAreaCode.startsWith("KMA:")) {
            return new StoredArea(SEOUL_SIDO_AREA_CODE, "SIDO");
        }
        if (spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN) {
            StoredArea areaByStationName = resolveAirKoreaArea(row, sourceAreaName);
            if (areaByStationName.areaCode() != null) {
                return areaByStationName;
            }
        }
        if (sourceAreaCode == null || sourceAreaCode.isBlank()) {
            return new StoredArea(null, null);
        }
        String sql = """
                SELECT area_code, level
                FROM public.sd_area_code
                WHERE area_code = :sourceAreaCode
                   OR (
                       LENGTH(:sourceAreaCode) = 8
                       AND eupmyeondong_code = :sourceAreaCode
                   )
                   OR (
                       LENGTH(:sourceAreaCode) = 5
                       AND sigungu_code = :sourceAreaCode
                   )
                   OR (
                       LENGTH(:sourceAreaCode) = 10
                       AND SUBSTRING(:sourceAreaCode, 6, 5) = '00000'
                       AND sigungu_code = SUBSTRING(:sourceAreaCode, 1, 5)
                   )
                   OR (
                       LENGTH(:sourceAreaCode) = 2
                       AND sido_code = :sourceAreaCode
                   )
                   OR (
                       LENGTH(:sourceAreaCode) = 10
                       AND SUBSTRING(:sourceAreaCode, 9, 2) = '00'
                       AND eupmyeondong_code = SUBSTRING(:sourceAreaCode, 1, 8)
                   )
                ORDER BY
                    CASE
                        WHEN area_code = :sourceAreaCode THEN 0
                        WHEN LENGTH(:sourceAreaCode) = 10
                             AND SUBSTRING(:sourceAreaCode, 9, 2) = '00'
                             AND eupmyeondong_code = SUBSTRING(:sourceAreaCode, 1, 8) THEN 1
                        ELSE 1
                    END,
                    area_code
                LIMIT 1
                """;
        List<StoredArea> areas = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("sourceAreaCode", sourceAreaCode.trim()),
                (rs, rowNum) -> new StoredArea(rs.getString("area_code"), rs.getString("level")));
        return areas.isEmpty() ? new StoredArea(null, null) : areas.get(0);
    }

    private StoredArea resolveAirKoreaArea(JsonNode row, String sourceAreaName) {
        if (sourceAreaName == null || sourceAreaName.isBlank()) {
            return new StoredArea(null, null);
        }
        String stationName = airKoreaStationKey(sourceAreaName);
        if (stationName.isBlank()) {
            return new StoredArea(null, null);
        }
        String sidoCode = blankToNull(firstText(row, "sidoCode"));
        String sql = """
                WITH matched_area AS (
                    SELECT area_code, level, 1 AS priority
                    FROM public.sd_area_code
                    WHERE level = 'SIGUNGU'
                      AND is_active = TRUE
                      AND (:sidoCode IS NULL OR sido_code = :sidoCode)
                      AND (
                          name = :stationName
                          OR regexp_replace(name, '(시|군|구)$', '') = :stationName
                          OR name = :stationName || '시'
                          OR name = :stationName || '군'
                          OR name = :stationName || '구'
                      )

                    UNION ALL

                    SELECT sigungu.area_code, sigungu.level, 2 AS priority
                    FROM public.sd_area_code station
                    JOIN public.sd_area_code sigungu
                        ON sigungu.level = 'SIGUNGU'
                       AND sigungu.is_active = TRUE
                       AND sigungu.sigungu_code = station.sigungu_code
                    WHERE station.level IN ('EUPMYEONDONG', 'RI')
                      AND station.is_active = TRUE
                      AND (:sidoCode IS NULL OR station.sido_code = :sidoCode)
                      AND station.name = :stationName
                )
                SELECT area_code, level
                FROM matched_area
                ORDER BY priority, area_code
                LIMIT 1
                """;
        List<StoredArea> areas = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("sidoCode", sidoCode)
                        .addValue("stationName", stationName),
                (rs, rowNum) -> new StoredArea(rs.getString("area_code"), rs.getString("level")));
        return areas.isEmpty() ? new StoredArea(null, null) : areas.get(0);
    }

    private StoredArea resolveAirKoreaSidoFallback(JsonNode row) {
        String sidoAreaCode = blankToNull(firstText(row, "sidoAreaCode"));
        if (sidoAreaCode != null) {
            return new StoredArea(sidoAreaCode, "SIDO");
        }
        String sidoCode = blankToNull(firstText(row, "sidoCode"));
        if (sidoCode == null) {
            return new StoredArea(null, null);
        }
        String sql = """
                SELECT area_code, level
                FROM public.sd_area_code
                WHERE level = 'SIDO'
                  AND sido_code = :sidoCode
                  AND is_active = TRUE
                LIMIT 1
                """;
        List<StoredArea> areas = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("sidoCode", sidoCode),
                (rs, rowNum) -> new StoredArea(rs.getString("area_code"), rs.getString("level")));
        return areas.isEmpty() ? new StoredArea(null, null) : areas.get(0);
    }

    private void deleteExistingObservation(MapSqlParameterSource params) {
        jdbcTemplate.update(deleteExistingObservationSql(), params);
    }

    private void deleteExistingObservations(MapSqlParameterSource[] params) {
        if (params.length == 0) {
            return;
        }
        jdbcTemplate.batchUpdate(deleteExistingObservationSql(), params);
    }

    private String deleteExistingObservationSql() {
        return """
                DELETE FROM public.sd_dashboard_area_observation
                WHERE dataset_code = :datasetCode
                  AND metric_code = :metricCode
                  AND COALESCE(area_code, '') = COALESCE(:areaCode, '')
                  AND COALESCE(source_area_code, '') = COALESCE(:sourceAreaCode, '')
                  AND COALESCE(grid_x, '') = COALESCE(:gridX, '')
                  AND COALESCE(grid_y, '') = COALESCE(:gridY, '')
                  AND base_date = :baseDate
                  AND COALESCE(base_hour, '') = COALESCE(:baseHour, '')
                  AND COALESCE(observed_at, TIMESTAMP '1900-01-01 00:00:00')
                      = COALESCE(:observedAt, TIMESTAMP '1900-01-01 00:00:00')
                  AND md5(dimensions::text) = md5(CAST(:dimensions AS jsonb)::text)
                """;
    }

    private int deleteDashboardObservationsForStatsMonth(SourceSpec spec, String statsYm, List<SigunguArea> areas) {
        if (areas.isEmpty()) {
            return 0;
        }
        List<String> sigunguCodes = areas.stream()
                .map(SigunguArea::sigunguCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        String areaFilter = sigunguCodes.isEmpty() ? "" : """
                  AND (
                      SUBSTRING(COALESCE(source_area_code, ''), 1, 5) IN (:sigunguCodes)
                      OR SUBSTRING(COALESCE(area_code, ''), 1, 5) IN (:sigunguCodes)
                  )
                """;
        String sql = """
                DELETE FROM public.sd_dashboard_area_observation
                WHERE dataset_code = :datasetCode
                  AND base_date = :baseDate
                  %s
                """.formatted(areaFilter);
        LocalDate baseDate = YearMonth.parse(statsYm, STATS_YM_FORMAT).atEndOfMonth();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode())
                .addValue("baseDate", baseDate);
        if (!sigunguCodes.isEmpty()) {
            params.addValue("sigunguCodes", sigunguCodes);
        }
        return jdbcTemplate.update(sql, params);
    }

    private int deleteDashboardObservationsForScope(SourceSpec spec, MoisAreaScope scope) {
        String sidoCode = blankToNull(scope.sidoCode());
        String sigunguCode = blankToNull(scope.sigunguCode());
        String areaFilter = "";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("datasetCode", spec.datasetCode());
        if (sidoCode != null || sigunguCode != null) {
            String codeFilter = "";
            if (sidoCode != null) {
                codeFilter += " AND c.sido_code = :sidoCode\n";
                params.addValue("sidoCode", sidoCode);
            }
            if (sigunguCode != null) {
                codeFilter += " AND c.sigungu_code = :sigunguCode\n";
                params.addValue("sigunguCode", sigunguCode);
            }
            areaFilter = """
                      AND EXISTS (
                          SELECT 1
                          FROM public.sd_area_code c
                          WHERE c.area_code = o.area_code
                            %s
                      )
                    """.formatted(codeFilter);
        }
        String sql = """
                DELETE FROM public.sd_dashboard_area_observation o
                WHERE o.dataset_code = :datasetCode
                  %s
                """.formatted(areaFilter);
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
        if (isMoisLegalDongSpec(spec)) {
            params.put("numOfRows", MAX_NUM_OF_ROWS);
            params.put("lv", "3");
        }
        if (spec == SourceSpec.KMA_VILAGE_FCST_MAIN) {
            applyKmaUltraSrtNcstBaseTime(params);
        }
    }

    private void applyKmaUltraSrtNcstBaseTime(Map<String, Object> params) {
        LocalDateTime baseDateTime = LocalDateTime.now(SEOUL_ZONE)
                .minusMinutes(45)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        params.put("base_date", baseDateTime.toLocalDate().format(DATE_FORMAT));
        params.put("base_time", baseDateTime.format(KMA_BASE_TIME_FORMAT));
    }

    private boolean isMoisLegalDongSpec(SourceSpec spec) {
        return spec == SourceSpec.MOIS_ADMM_AVG_AGE_MAIN
                || spec == SourceSpec.MOIS_ADMM_HSMB_HH_MAIN
                || spec == SourceSpec.MOIS_ADMM_POP_CHANGE_MAIN;
    }

    private boolean usesAreaCollectionScope(SourceSpec spec) {
        return isMoisLegalDongSpec(spec)
                || spec == SourceSpec.AIRKOREA_AIR_QUALITY_MAIN
                || spec == SourceSpec.KMA_VILAGE_FCST_MAIN;
    }

    private MoisAreaScope resolveMoisLegalDongScope(String keyword) {
        return resolveAreaCollectionScope(keyword);
    }

    private MoisAreaScope resolveAreaCollectionScope(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new MoisAreaScope(null, null);
        }
        if (keyword != null && keyword.equalsIgnoreCase("all")) {
            return new MoisAreaScope(null, null);
        }
        String normalized = keyword.trim();
        if (normalized.matches("\\d{2}")) {
            return new MoisAreaScope(normalized, null);
        }
        if (normalized.matches("\\d{5}")) {
            return new MoisAreaScope(null, normalized);
        }
        if (normalized.matches("\\d{10}")) {
            return resolveAreaCodeScope(normalized);
        }
        return resolveAreaNameScope(normalized).orElse(new MoisAreaScope(null, null));
    }

    private MoisAreaScope resolveAreaCodeScope(String areaCode) {
        String sql = """
                SELECT sido_code, sigungu_code, level
                FROM public.sd_area_code
                WHERE area_code = :areaCode
                  AND is_active = TRUE
                LIMIT 1
                """;
        List<MoisAreaScope> scopes = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("areaCode", areaCode),
                (rs, rowNum) -> {
                    String level = rs.getString("level");
                    String sidoCode = rs.getString("sido_code");
                    String sigunguCode = rs.getString("sigungu_code");
                    if ("SIDO".equals(level)) {
                        return new MoisAreaScope(sidoCode, null);
                    }
                    return new MoisAreaScope(null, sigunguCode);
                });
        if (!scopes.isEmpty()) {
            return scopes.get(0);
        }
        String sigunguCode = areaCode.length() >= 5 ? areaCode.substring(0, 5) : null;
        return new MoisAreaScope(null, sigunguCode);
    }

    private Optional<MoisAreaScope> resolveAreaNameScope(String areaName) {
        String sql = """
                SELECT sido_code, sigungu_code, level
                FROM public.sd_area_code
                WHERE is_active = TRUE
                  AND (name = :areaName OR full_name = :areaName)
                ORDER BY
                    CASE level
                        WHEN 'SIDO' THEN 1
                        WHEN 'SIGUNGU' THEN 2
                        ELSE 3
                    END
                LIMIT 1
                """;
        List<MoisAreaScope> scopes = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("areaName", areaName),
                (rs, rowNum) -> {
                    String level = rs.getString("level");
                    String sidoCode = rs.getString("sido_code");
                    String sigunguCode = rs.getString("sigungu_code");
                    if ("SIDO".equals(level)) {
                        return new MoisAreaScope(sidoCode, null);
                    }
                    return new MoisAreaScope(null, sigunguCode);
                });
        return scopes.isEmpty() ? Optional.empty() : Optional.of(scopes.get(0));
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

    private List<String> moisLegalDongDatasetCodes() {
        return List.of(
                SourceSpec.MOIS_ADMM_AVG_AGE_MAIN.datasetCode(),
                SourceSpec.MOIS_ADMM_HSMB_HH_MAIN.datasetCode(),
                SourceSpec.MOIS_ADMM_POP_CHANGE_MAIN.datasetCode());
    }

    private Map<String, Object> countMoisLegalDongObservationRows(List<String> datasetCodes) {
        String sql = """
                SELECT
                    COUNT(*)::bigint AS total_count,
                    COUNT(*) FILTER (
                        WHERE COALESCE(raw_payload ->> 'stdgCd', '') ~ '^[0-9]{10}$'
                          AND SUBSTRING(raw_payload ->> 'stdgCd' FROM 6 FOR 5) <> '00000'
                    )::bigint AS legal_dong_count,
                    COUNT(*) FILTER (
                        WHERE COALESCE(raw_payload ->> 'stdgCd', '') !~ '^[0-9]{10}$'
                           OR SUBSTRING(raw_payload ->> 'stdgCd' FROM 6 FOR 5) = '00000'
                    )::bigint AS invalid_count
                FROM public.sd_dashboard_area_observation
                WHERE dataset_code IN (:datasetCodes)
                """;
        return jdbcTemplate.queryForMap(sql, new MapSqlParameterSource("datasetCodes", datasetCodes));
    }

    private int deleteInvalidMoisLegalDongObservations(List<String> datasetCodes) {
        String sql = """
                DELETE FROM public.sd_dashboard_area_observation
                WHERE dataset_code IN (:datasetCodes)
                  AND (
                      COALESCE(raw_payload ->> 'stdgCd', '') !~ '^[0-9]{10}$'
                      OR SUBSTRING(raw_payload ->> 'stdgCd' FROM 6 FOR 5) = '00000'
                  )
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource("datasetCodes", datasetCodes));
    }

    private int normalizeMoisLegalDongObservations(List<String> datasetCodes) {
        String sql = """
                UPDATE public.sd_dashboard_area_observation
                SET area_level = :areaLevel,
                    source_area_code = raw_payload ->> 'stdgCd',
                    source_area_name = COALESCE(NULLIF(raw_payload ->> 'stdgNm', ''), source_area_name),
                    updated_at = CURRENT_TIMESTAMP
                WHERE dataset_code IN (:datasetCodes)
                  AND COALESCE(raw_payload ->> 'stdgCd', '') ~ '^[0-9]{10}$'
                  AND SUBSTRING(raw_payload ->> 'stdgCd' FROM 6 FOR 5) <> '00000'
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("datasetCodes", datasetCodes)
                .addValue("areaLevel", MOIS_LEGAL_DONG_AREA_LEVEL));
    }

    private int normalizeMoisLegalDongDatasetCatalog(List<String> datasetCodes) {
        String sql = """
                UPDATE public.sd_dashboard_dataset
                SET default_area_level = :areaLevel,
                    spatial_join_strategy = 'LEGAL_DONG_CODE',
                    updated_at = CURRENT_TIMESTAMP
                WHERE dataset_code IN (:datasetCodes)
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("datasetCodes", datasetCodes)
                .addValue("areaLevel", MOIS_LEGAL_DONG_AREA_LEVEL));
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
        if (date.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return LocalDate.parse(date.substring(0, 10));
        }
        if (date.matches("\\d{6}")) {
            return YearMonth.parse(date, STATS_YM_FORMAT).atEndOfMonth();
        }
        if (date.matches("\\d{8}")) {
            return LocalDate.parse(date, DATE_FORMAT);
        }
        return LocalDate.now(SEOUL_ZONE);
    }

    private String baseHour(JsonNode row) {
        String dateTime = firstText(row, "dataTime", "tm");
        if (dateTime.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*")) {
            return dateTime.substring(11, 13);
        }
        String hour = firstText(row, "baseTime", "base_time", "fcstTime", "hour");
        if (hour.matches("\\d{4}")) {
            return hour.substring(0, 2);
        }
        if (hour.matches("\\d{1,2}")) {
            return hour.length() == 1 ? "0" + hour : hour;
        }
        return null;
    }

    private LocalDateTime observedAt(JsonNode row) {
        String dataTime = firstText(row, "dataTime", "tm");
        if (dataTime.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*")) {
            return LocalDateTime.parse(dataTime.substring(0, 16), AIRKOREA_DATE_TIME_FORMAT);
        }

        String baseDate = firstText(row, "baseDate", "base_date", "fcstDate");
        String baseTime = firstText(row, "baseTime", "base_time", "fcstTime");
        if (baseDate.matches("\\d{8}") && baseTime.matches("\\d{4}")) {
            return LocalDateTime.parse(baseDate + baseTime, KMA_BASE_DATE_TIME_FORMAT);
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

    private String airKoreaSidoName(SidoArea area) {
        return switch (area.sidoCode()) {
            case "11" -> "서울";
            case "26" -> "부산";
            case "27" -> "대구";
            case "28" -> "인천";
            case "29" -> "광주";
            case "30" -> "대전";
            case "31" -> "울산";
            case "36" -> "세종";
            case "41" -> "경기";
            case "42", "51" -> "강원";
            case "43" -> "충북";
            case "44" -> "충남";
            case "45" -> "전북";
            case "46" -> "전남";
            case "47" -> "경북";
            case "48" -> "경남";
            case "49" -> "제주";
            default -> area.name();
        };
    }

    private String airKoreaStationKey(String stationName) {
        if (stationName == null) {
            return "";
        }
        return stationName
                .replaceAll("\\(.*?\\)", "")
                .replace("측정소", "")
                .trim();
    }

    private KmaGrid toKmaGrid(double longitude, double latitude) {
        double re = 6371.00877 / 5.0;
        double slat1 = 30.0 * Math.PI / 180.0;
        double slat2 = 60.0 * Math.PI / 180.0;
        double olon = 126.0 * Math.PI / 180.0;
        double olat = 38.0 * Math.PI / 180.0;
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);
        double ra = Math.tan(Math.PI * 0.25 + (latitude * Math.PI / 180.0) * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = longitude * Math.PI / 180.0 - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;
        int x = (int) Math.floor(ra * Math.sin(theta) + 43.0 + 0.5);
        int y = (int) Math.floor(ro - ra * Math.cos(theta) + 136.0 + 0.5);
        return new KmaGrid(x, y);
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

    private record SigunguArea(String areaCode, String sidoCode, String sigunguCode, String name, String fullName) {
    }

    private record MoisAreaScope(String sidoCode, String sigunguCode) {
    }

    private record WeatherGridArea(
            String areaCode,
            String areaLevel,
            String name,
            String fullName,
            double longitude,
            double latitude,
            int gridX,
            int gridY) {
    }

    private record KmaGrid(int x, int y) {
    }

    private record StoredArea(String areaCode, String level) {
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
        private static final SourceSpec MOIS_ADMM_AVG_AGE_MAIN = moisLegalDong("MOIS_ADMM_AVG_AGE", "MOIS_ADMM_AVG_AGE_MAIN", "/1741000/stdgSexdPpltnAvrgAge/selectStdgSexdPpltnAvrgAge");
        private static final SourceSpec MOIS_ADMM_HSMB_HH_MAIN = moisLegalDong("MOIS_ADMM_HSMB_HH", "MOIS_ADMM_HSMB_HH_MAIN", "/1741000/stdgHsmbHh/selectStdgHsmbHh");
        private static final SourceSpec MOIS_ADMM_POP_CHANGE_MAIN = moisLegalDong("MOIS_ADMM_POP_CHANGE", "MOIS_ADMM_POP_CHANGE_MAIN", "/1741000/stdgSexdPpltnIrds/selectStdgSexdPpltnIrds");
        private static final SourceSpec MOIS_ADMM_PPLTN_HH_STUS_MAIN = mois("MOIS_ADMM_PPLTN_HH_STUS", "MOIS_ADMM_PPLTN_HH_STUS_MAIN", "/1741000/admmPpltnHhStus/selectAdmmPpltnHhStus");
        private static final SourceSpec MOIS_ADMM_SEXD_AGE_PPLTN_MAIN = new SourceSpec("MOIS_ADMM_SEXD_AGE_PPLTN", "MOIS_ADMM_SEXD_AGE_PPLTN_MAIN", StorageType.OBSERVATION, "EUPMYEONDONG", "http://apis.data.go.kr", "/1741000/admmSexdAgePpltn/selectAdmmSexdAgePpltn", List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), moisParams(), null);
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
        private static final SourceSpec STANDARD_BUS_STOP_MAIN = dataGo("STANDARD_BUS_STOP", "STANDARD_BUS_STOP_MAIN", StorageType.FEATURE, null, "/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList", p("_type", "json", "gpsLati", "37.5665", "gpsLong", "126.9780"));
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
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "https://apis.data.go.kr", path, List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), params, null);
        }

        private static SourceSpec standard(String sourceCode, String datasetCode, StorageType storageType, String defaultAreaLevel, String path, Map<String, String> params) {
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "https://api.data.go.kr", path, List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), params, null);
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
            return new SourceSpec(sourceCode, datasetCode, StorageType.OBSERVATION, "EUPMYEONDONG", "http://apis.data.go.kr", path, List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), moisParams(), null);
        }

        private static SourceSpec moisLegalDong(String sourceCode, String datasetCode, String path) {
            return new SourceSpec(sourceCode, datasetCode, StorageType.OBSERVATION, MOIS_LEGAL_DONG_AREA_LEVEL, "http://apis.data.go.kr", path, List.of(new AuthParam("serviceKey", "MOIS_OPEN_API_KEY")), moisLegalDongParams(), null);
        }

        private static SourceSpec blocked(String sourceCode, String datasetCode, StorageType storageType, String defaultAreaLevel, String reason) {
            return new SourceSpec(sourceCode, datasetCode, storageType, defaultAreaLevel, "", "", List.of(), Map.of(), reason);
        }

        private static Map<String, String> moisParams() {
            return p("admmCd", "0000000000", "srchFrYm", "{statsYm}", "srchToYm", "{statsYm}", "lv", "1", "regSeCd", "1", "type", "JSON");
        }

        private static Map<String, String> moisLegalDongParams() {
            return p("stdgCd", "0000000000", "srchFrYm", "{statsYm}", "srchToYm", "{statsYm}", "lv", "3", "type", "JSON");
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
