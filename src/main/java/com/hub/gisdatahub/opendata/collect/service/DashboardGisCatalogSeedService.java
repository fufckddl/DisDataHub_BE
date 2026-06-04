package com.hub.gisdatahub.opendata.collect.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.dashboard.dto.DashboardGisCatalogSeedResult;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDataSourceSeed;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDatasetSeed;
import com.hub.gisdatahub.dashboard.dto.DashboardGisMetricSeed;
import com.hub.gisdatahub.dashboard.mapper.DashboardGisDataMapper;

@Service
public class DashboardGisCatalogSeedService {

    private static final String CANDIDATE_MARKDOWN = "sql/20260529_gis_dashboard_data_sources.md";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private final DashboardGisDataMapper dashboardGisDataMapper;
    private final ObjectMapper objectMapper;

    public DashboardGisCatalogSeedService(
            DashboardGisDataMapper dashboardGisDataMapper,
            ObjectMapper objectMapper) {
        this.dashboardGisDataMapper = dashboardGisDataMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DashboardGisCatalogSeedResult seedCandidateCatalog() {
        List<CandidateRow> candidates = readCandidateRows();
        int upsertedSources = 0;
        int upsertedDatasets = 0;
        int upsertedMetrics = 0;

        for (CandidateRow candidate : candidates) {
            String datasetCode = truncate(candidate.sourceCode() + "_MAIN", 120);

            upsertedSources += dashboardGisDataMapper.upsertDataSource(toDataSourceSeed(candidate));
            upsertedDatasets += dashboardGisDataMapper.upsertDataset(toDatasetSeed(candidate, datasetCode));

            List<String> metricNames = parseMetricNames(candidate.metricHint());
            for (int i = 0; i < metricNames.size(); i++) {
                upsertedMetrics += dashboardGisDataMapper.upsertMetric(toMetricSeed(candidate, datasetCode, metricNames.get(i), i));
            }
        }

        return DashboardGisCatalogSeedResult.builder()
                .parsedSourceCount(candidates.size())
                .upsertedSourceCount(upsertedSources)
                .upsertedDatasetCount(upsertedDatasets)
                .upsertedMetricCount(upsertedMetrics)
                .build();
    }

    private DashboardGisDataSourceSeed toDataSourceSeed(CandidateRow candidate) {
        String sourceText = combined(candidate);
        String officialUrl = firstUrl(candidate.officialEvidence());
        boolean hasPointCoordinate = containsAny(sourceText, "위도", "경도", "좌표", "mapx", "mapy", "gisx", "gisy");
        boolean hasGeometry = hasPointCoordinate || containsAny(sourceText, "wms", "wfs", "geometry", "geom", "폴리곤", "면", "구역", "레이어");

        return DashboardGisDataSourceSeed.builder()
                .sourceCode(candidate.sourceCode())
                .sourceName(candidate.dataName())
                .providerName(inferProviderName(candidate.sourceCode(), officialUrl))
                .providerType(inferProviderType(candidate.sourceCode(), candidate.displayMode(), officialUrl))
                .sourceCategory(candidate.category())
                .officialUrl(officialUrl)
                .apiEndpoint(null)
                .apiType(inferApiType(candidate.sourceCode(), candidate.displayMode(), candidate.spatialKey(), officialUrl))
                .dataFormat(inferDataFormat(candidate.sourceCode(), candidate.displayMode(), officialUrl))
                .authType(inferAuthType(candidate.sourceCode(), officialUrl))
                .spatialCoverage("NATIONWIDE")
                .spatialGranularity(candidate.spatialKey())
                .temporalGranularity(candidate.updateCycle())
                .updateCycle(candidate.updateCycle())
                .coordinateSystem(inferCoordinateSystem(candidate.spatialKey()))
                .hasGeometry(hasGeometry)
                .hasPointCoordinate(hasPointCoordinate)
                .collectionDifficulty(inferDifficulty(candidate.difficulty()))
                .priority(candidate.priority())
                .licenseNote("공식 근거 URL의 이용조건 확인 필요")
                .quotaNote("호출 제한은 원천 API별 운영정책 확인 필요")
                .retentionDays(null)
                .verificationStatus("CANDIDATE")
                .verificationNote("Markdown 후보표에서 자동 등록됨. 수집기 구현 전 원천별 파라미터와 응답 검증 필요")
                .metadata(json(Map.of(
                        "dataName", candidate.dataName(),
                        "displayMode", candidate.displayMode(),
                        "metricHint", candidate.metricHint(),
                        "spatialKey", candidate.spatialKey(),
                        "officialEvidence", candidate.officialEvidence(),
                        "seedFile", CANDIDATE_MARKDOWN)))
                .build();
    }

    private DashboardGisDatasetSeed toDatasetSeed(CandidateRow candidate, String datasetCode) {
        return DashboardGisDatasetSeed.builder()
                .sourceCode(candidate.sourceCode())
                .datasetCode(datasetCode)
                .datasetName(candidate.dataName())
                .dashboardLayerType(inferLayerType(candidate.displayMode(), candidate.spatialKey()))
                .dashboardMetricHint(candidate.metricHint())
                .defaultGeometryType(inferGeometryType(candidate.displayMode(), candidate.spatialKey()))
                .defaultAreaLevel(inferDefaultAreaLevel(candidate.spatialKey()))
                .spatialJoinStrategy(inferSpatialJoinStrategy(candidate.spatialKey()))
                .collectionPolicy("CATALOG_FIRST_COLLECTOR_PENDING")
                .displayPriority(candidate.priority())
                .isInitialCandidate(true)
                .metadata(json(Map.of(
                        "category", candidate.category(),
                        "displayMode", candidate.displayMode(),
                        "updateCycle", candidate.updateCycle(),
                        "difficulty", candidate.difficulty())))
                .build();
    }

    private DashboardGisMetricSeed toMetricSeed(
            CandidateRow candidate,
            String datasetCode,
            String metricName,
            int index) {
        String valueType = inferValueType(metricName);
        return DashboardGisMetricSeed.builder()
                .datasetCode(datasetCode)
                .metricCode("METRIC_%03d".formatted(index + 1))
                .metricName(truncate(metricName, 240))
                .valueType(valueType)
                .unit("NUMBER".equals(valueType) ? inferUnit(metricName, candidate.category()) : null)
                .chartGroup(candidate.category())
                .sortOrder(index)
                .isDefault(index == 0)
                .metadata(json(Map.of(
                        "sourceCode", candidate.sourceCode(),
                        "rawMetricHint", candidate.metricHint())))
                .build();
    }

    private List<CandidateRow> readCandidateRows() {
        try {
            ClassPathResource resource = new ClassPathResource(CANDIDATE_MARKDOWN);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            List<CandidateRow> rows = new ArrayList<>();
            boolean inCandidateTable = false;

            for (String line : content.lines().toList()) {
                if (line.startsWith("| 우선 | 분야 | source_code")) {
                    inCandidateTable = true;
                    continue;
                }
                if (!inCandidateTable) {
                    continue;
                }
                if (!line.startsWith("|")) {
                    if (!rows.isEmpty()) {
                        break;
                    }
                    continue;
                }
                if (line.contains("---")) {
                    continue;
                }

                List<String> cells = markdownCells(line);
                if (cells.size() < 10 || !isInteger(cells.get(0))) {
                    continue;
                }

                rows.add(new CandidateRow(
                        Integer.parseInt(cells.get(0)),
                        cells.get(1),
                        stripBackticks(cells.get(2)),
                        cells.get(3),
                        cells.get(4),
                        cells.get(5),
                        cells.get(6),
                        cells.get(7),
                        cells.get(8),
                        cells.get(9)));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("대시보드 GIS 후보 Markdown을 읽을 수 없습니다: " + CANDIDATE_MARKDOWN, e);
        }
    }

    private List<String> markdownCells(String line) {
        String normalized = line.trim();
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        String[] split = normalized.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String cell : split) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private List<String> parseMetricNames(String rawMetricHint) {
        String normalized = rawMetricHint == null ? "" : rawMetricHint
                .replace(" 및 ", ",")
                .replace("/", ",")
                .replace("·", ",")
                .replace("+", ",");

        List<String> names = new ArrayList<>();
        for (String token : normalized.split("[,;]")) {
            String name = token.trim();
            if (!name.isBlank()) {
                names.add(name);
            }
            if (names.size() >= 12) {
                break;
            }
        }
        if (names.isEmpty()) {
            names.add("주요지표");
        }
        return names;
    }

    private String inferProviderName(String sourceCode, String officialUrl) {
        if (sourceCode.startsWith("MOIS_") || sourceCode.startsWith("SAFE_") || sourceCode.startsWith("JUSO_")) {
            return "행정안전부";
        }
        if (sourceCode.startsWith("KMA_")) {
            return "기상청";
        }
        if (sourceCode.startsWith("AIRKOREA_") || sourceCode.startsWith("KECO_")) {
            return "한국환경공단";
        }
        if (sourceCode.startsWith("SEMAS_")) {
            return "소상공인시장진흥공단";
        }
        if (sourceCode.startsWith("STANDARD_")) {
            return "공공데이터포털 표준데이터";
        }
        if (sourceCode.startsWith("KOROAD_")) {
            return "도로교통공단";
        }
        if (sourceCode.startsWith("MOLIT_")) {
            return "국토교통부";
        }
        if (sourceCode.startsWith("KALIS_")) {
            return "국토안전관리원";
        }
        if (sourceCode.startsWith("KTO_")) {
            return "한국관광공사";
        }
        if (sourceCode.startsWith("HERITAGE_")) {
            return "국가유산청";
        }
        if (sourceCode.startsWith("ECOBANK_")) {
            return "국립생태원";
        }
        if (sourceCode.startsWith("KWATER_")) {
            return "한국수자원공사";
        }
        if (sourceCode.startsWith("SGIS_") || sourceCode.startsWith("KOSIS_")) {
            return "통계청";
        }
        if (sourceCode.startsWith("VWORLD_")) {
            return "공간정보오픈플랫폼";
        }
        return officialUrl.contains("data.go.kr") ? "공공데이터포털" : "공식 제공기관";
    }

    private String inferProviderType(String sourceCode, String displayMode, String officialUrl) {
        String text = (sourceCode + " " + displayMode + " " + officialUrl).toLowerCase(Locale.ROOT);
        if (officialUrl.contains("/standard.do")) {
            return "STANDARD_DATA";
        }
        if (officialUrl.contains("fileData.do")) {
            return "STATIC_DOWNLOAD";
        }
        if (text.contains("wms") || text.contains("wfs") || text.contains("wmts")) {
            return "LINK_SERVICE";
        }
        return "PUBLIC_OPEN_API";
    }

    private String inferApiType(String sourceCode, String displayMode, String spatialKey, String officialUrl) {
        String text = (sourceCode + " " + displayMode + " " + spatialKey + " " + officialUrl).toLowerCase(Locale.ROOT);
        if (officialUrl.contains("/standard.do")) {
            return "STANDARD";
        }
        if (officialUrl.contains("fileData.do")) {
            return "FILE";
        }
        if ((text.contains("wms") && text.contains("wfs")) || text.contains("mixed")) {
            return "MIXED";
        }
        if (text.contains("wmts")) {
            return "WMTS";
        }
        if (text.contains("wms")) {
            return "WMS";
        }
        if (text.contains("wfs")) {
            return "WFS";
        }
        return "REST";
    }

    private String inferDataFormat(String sourceCode, String displayMode, String officialUrl) {
        String text = (sourceCode + " " + displayMode + " " + officialUrl).toLowerCase(Locale.ROOT);
        if (text.contains("wms") || text.contains("wfs")) {
            return "OGC WMS/WFS";
        }
        if (officialUrl.contains("/standard.do")) {
            return "표준데이터 CSV/JSON";
        }
        if (officialUrl.contains("fileData.do")) {
            return "FILE";
        }
        return "JSON/XML";
    }

    private String inferAuthType(String sourceCode, String officialUrl) {
        if (sourceCode.startsWith("SGIS_")) {
            return "CONSUMER_KEY_SECRET";
        }
        if (sourceCode.startsWith("KOSIS_")) {
            return "API_KEY";
        }
        if (sourceCode.startsWith("JUSO_")) {
            return "CONFIRM_KEY";
        }
        if (sourceCode.startsWith("VWORLD_")) {
            return "VWORLD_API_KEY";
        }
        if (officialUrl.contains("data.go.kr")) {
            return "MOIS_OPEN_API_KEY";
        }
        return "OFFICIAL_API_KEY_REQUIRED";
    }

    private String inferCoordinateSystem(String spatialKey) {
        String text = lower(spatialKey);
        if (text.contains("nx") || text.contains("ny") || text.contains("격자")) {
            return "KMA_GRID";
        }
        if (text.contains("위도") || text.contains("경도") || text.contains("mapx") || text.contains("mapy")) {
            return "WGS84";
        }
        if (text.contains("wms") || text.contains("wfs") || text.contains("geometry")) {
            return "SOURCE_GEOMETRY";
        }
        if (text.contains("좌표") || text.contains("gisx") || text.contains("gisy")) {
            return "SOURCE_COORDINATE";
        }
        if (text.contains("행정기관코드") || text.contains("행정구역코드")) {
            return "ADMIN_CODE";
        }
        return "SOURCE_DEPENDENT";
    }

    private String inferLayerType(String displayMode, String spatialKey) {
        String text = lower(displayMode + " " + spatialKey);
        if (text.contains("색상지도")) {
            return "CHOROPLETH";
        }
        if (text.contains("히트맵")) {
            return "HEATMAP";
        }
        if (text.contains("라인") || text.contains("구간") || text.contains("도로")) {
            return "LINE";
        }
        if (text.contains("점") || text.contains("위도") || text.contains("경도")) {
            return "POINT";
        }
        if (text.contains("wms")) {
            return "RASTER";
        }
        if (text.contains("wfs") || text.contains("폴리곤") || text.contains("면")) {
            return "POLYGON";
        }
        if (text.contains("랭킹")) {
            return "RANKING";
        }
        if (text.contains("추이") || text.contains("시계열")) {
            return "TIME_SERIES";
        }
        return "TABLE";
    }

    private String inferGeometryType(String displayMode, String spatialKey) {
        String text = lower(displayMode + " " + spatialKey);
        if (text.contains("라인") || text.contains("구간")) {
            return "LINESTRING";
        }
        if (text.contains("wms")) {
            return "RASTER";
        }
        if (text.contains("wfs") || text.contains("폴리곤") || text.contains("면") || text.contains("구역")) {
            return "POLYGON";
        }
        if (text.contains("점") || text.contains("위도") || text.contains("경도") || text.contains("좌표")) {
            return "POINT";
        }
        return "ADMIN_AREA";
    }

    private String inferDefaultAreaLevel(String spatialKey) {
        String text = lower(spatialKey);
        if (text.contains("법정동")) {
            return "LEGAL_DONG";
        }
        if (text.contains("읍면동") || text.contains("행정동") || text.contains("행정기관코드")) {
            return "EUPMYEONDONG";
        }
        if (text.contains("시군구")) {
            return "SIGUNGU";
        }
        if (text.contains("시도")) {
            return "SIDO";
        }
        if (text.contains("지역") || text.contains("주소")) {
            return "SIGUNGU";
        }
        return null;
    }

    private String inferSpatialJoinStrategy(String spatialKey) {
        String text = lower(spatialKey);
        if (text.contains("행정기관코드") || text.contains("행정구역코드")) {
            return "ADMIN_CODE";
        }
        if (text.contains("법정동코드")) {
            return "LEGAL_DONG_CODE";
        }
        if (text.contains("위도") || text.contains("경도") || text.contains("좌표") || text.contains("gisx") || text.contains("gisy")) {
            return "POINT_IN_POLYGON";
        }
        if (text.contains("주소")) {
            return "ADDRESS_GEOCODING";
        }
        if (text.contains("wms") || text.contains("wfs") || text.contains("geometry")) {
            return "EXTERNAL_GEOMETRY";
        }
        if (text.contains("격자") || text.contains("nx") || text.contains("ny")) {
            return "GRID_MAPPING";
        }
        return "MANUAL_MAPPING";
    }

    private String inferDifficulty(String difficulty) {
        String text = lower(difficulty);
        if (text.contains("낮")) {
            return "LOW";
        }
        if (text.contains("높")) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String inferValueType(String metricName) {
        String text = lower(metricName);
        if (text.contains("json") || text.contains("속성")) {
            return "JSON";
        }
        if (containsAny(text, "명", "id", "코드", "주소", "위치", "상호", "시설", "유형", "분류", "메시지", "레이어")) {
            return "TEXT";
        }
        return "NUMBER";
    }

    private String inferUnit(String metricName, String category) {
        String text = lower(metricName + " " + category);
        if (containsAny(text, "인구", "수용인원", "방문자", "남", "여")) {
            return "명";
        }
        if (containsAny(text, "건수", "사고", "점검", "발령")) {
            return "건";
        }
        if (text.contains("면적")) {
            return "㎡";
        }
        if (text.contains("기온")) {
            return "℃";
        }
        if (text.contains("속도")) {
            return "km/h";
        }
        if (containsAny(text, "pm10", "pm2.5", "o3", "no2")) {
            return "㎍/㎥";
        }
        if (containsAny(text, "비율", "저수율")) {
            return "%";
        }
        return null;
    }

    private String firstUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group().replaceAll("[)>.,]+$", "");
        }
        return "https://www.data.go.kr";
    }

    private String stripBackticks(String value) {
        return value == null ? "" : value.replace("`", "").trim();
    }

    private boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAny(String text, String... needles) {
        String lowerText = lower(text);
        for (String needle : needles) {
            if (lowerText.contains(lower(needle))) {
                return true;
            }
        }
        return false;
    }

    private String combined(CandidateRow candidate) {
        return String.join(" ",
                candidate.sourceCode(),
                candidate.dataName(),
                candidate.displayMode(),
                candidate.metricHint(),
                candidate.spatialKey(),
                candidate.officialEvidence());
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대시보드 GIS 카탈로그 metadata JSON 생성 실패", e);
        }
    }

    private record CandidateRow(
            int priority,
            String category,
            String sourceCode,
            String dataName,
            String displayMode,
            String metricHint,
            String spatialKey,
            String updateCycle,
            String difficulty,
            String officialEvidence) {
    }
}
