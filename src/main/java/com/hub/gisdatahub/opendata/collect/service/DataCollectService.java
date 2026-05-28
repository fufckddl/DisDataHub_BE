package com.hub.gisdatahub.opendata.collect.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.exception.DataCollectException;
import com.hub.gisdatahub.opendata.collect.client.DataCollectClient;
import com.hub.gisdatahub.opendata.collect.dto.mois.MoisResidentPopulationRow;
import com.hub.gisdatahub.opendata.collect.dto.seoul.SdotVisitorRow;
import com.hub.gisdatahub.opendata.collect.mapper.MoisResidentPopulationMapper;
import com.hub.gisdatahub.opendata.collect.mapper.SdotVisitorMapper;

@Service
public class DataCollectService {
    private static final Logger log = LoggerFactory.getLogger(DataCollectService.class);

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STATS_YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter SDOT_SENSING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");
    private static final DateTimeFormatter SDOT_REGISTERED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MOIS_PAGE_SIZE = 100;
    private static final int MOIS_MAX_ATTEMPTS = 2;
    private static final long MOIS_RETRY_DELAY_MILLIS = 1_000L;
    private static final String MOIS_ROOT_ADMM_CD = "0000000000";
    private static final String MOIS_DEFAULT_REG_SE_CD = "1";
    private static final String SDOT_VISITOR_COUNT = "IotVdata018";

    private final DataCollectException dataCollectException;
    private final DataCollectClient dataCollectClient;
    private final MoisResidentPopulationMapper moisResidentPopulationMapper;
    private final SdotVisitorMapper sdotVisitorMapper;
    private final ObjectMapper objectMapper;

    public DataCollectService(
        DataCollectException dataCollectException,
        DataCollectClient dataCollectClient,
        MoisResidentPopulationMapper moisResidentPopulationMapper,
        SdotVisitorMapper sdotVisitorMapper,
        ObjectMapper objectMapper
    ) {
        this.dataCollectException = dataCollectException;
        this.dataCollectClient = dataCollectClient;
        this.moisResidentPopulationMapper = moisResidentPopulationMapper;
        this.sdotVisitorMapper = sdotVisitorMapper;
        this.objectMapper = objectMapper;
    }

    // 스케줄러에서 월 1회 또는 수동 실행 시 호출하는 행안부 주민등록 인구 수집 진입점입니다.
    public int collectDailyResidentPopulation() {
        return collectResidentPopulation(null, MOIS_DEFAULT_REG_SE_CD, null);
    }

    // 행안부 행정동별(통반단위) 성/연령별 주민등록 인구수를 레벨별로 수집합니다.
    // lv: 1 광역시도, 2 시군구, 3 읍면동, 4 읍면동 통반. null/ALL이면 1~4와 TOTAL 집계를 모두 저장합니다.
    public int collectResidentPopulation(String statsYm, String regSeCd, String lv) {
        return collectResidentPopulation(statsYm, regSeCd, lv, null);
    }

    public int collectResidentPopulation(String statsYm, String regSeCd, String lv, String sidoCode) {
        String requestStatsYm = resolveStatsYm(statsYm);
        String requestRegSeCd = resolveMoisRegSeCd(regSeCd);
        List<String> levels = resolveMoisLevels(lv);
        boolean includeTotal = shouldIncludeTotal(lv);
        String requestSidoCode = normalizeSidoCode(sidoCode);
        int savedCount = 0;

        for (String level : levels) {
            savedCount += collectMoisResidentPopulationLevel(requestStatsYm, requestRegSeCd, level, requestSidoCode);
        }

        if (includeTotal) {
            savedCount += moisResidentPopulationMapper.upsertTotal(requestStatsYm, requestRegSeCd);
        }

        return savedCount;
    }

    private int collectMoisResidentPopulationLevel(String statsYm, String regSeCd, String level, String sidoCode) {
        if ("1".equals(level) && (sidoCode == null || sidoCode.isBlank())) {
            int savedCount = collectMoisResidentPopulationPages(MOIS_ROOT_ADMM_CD, statsYm, regSeCd, level);
            if (savedCount > 0) {
                return savedCount;
            }
        }

        int fallbackSavedCount = 0;
        for (String parentAdmmCd : findMoisParentAdmmCodes(level)) {
            if (parentAdmmCd != null
                    && !parentAdmmCd.isBlank()
                    && (sidoCode == null || sidoCode.isBlank() || parentAdmmCd.startsWith(sidoCode))) {
                fallbackSavedCount += collectMoisResidentPopulationPages(parentAdmmCd, statsYm, regSeCd, level);
            }
        }
        return fallbackSavedCount;
    }

    private String normalizeSidoCode(String sidoCode) {
        if (sidoCode == null || sidoCode.isBlank()) {
            return null;
        }
        String trimmed = sidoCode.trim();
        return trimmed.length() >= 2 ? trimmed.substring(0, 2) : trimmed;
    }

    private List<String> findMoisParentAdmmCodes(String level) {
        return switch (level) {
            case "2" -> moisResidentPopulationMapper.findSidoAdmmCodes();
            case "3" -> moisResidentPopulationMapper.findSigunguAdmmCodes();
            case "4" -> moisResidentPopulationMapper.findEupmyeondongAdmmCodes();
            default -> List.of();
        };
    }

    private int collectMoisResidentPopulationPages(String admmCd, String statsYm, String regSeCd, String level) {
        int pageNo = 1;
        int savedCount = 0;
        Map<String, String> areaCodeCache = new ConcurrentHashMap<>();

        while (true) {
            Optional<String> responseBody = callMoisResidentPopulationWithRetry(
                    admmCd,
                    statsYm,
                    statsYm,
                    level,
                    regSeCd,
                    pageNo,
                    MOIS_PAGE_SIZE);
            if (responseBody.isEmpty()) {
                break;
            }

            MoisPageResult pageResult = saveMoisResidentPopulation(responseBody.get(), level, regSeCd, statsYm, areaCodeCache);
            savedCount += pageResult.savedCount();

            if (pageResult.totalCount() <= pageNo * MOIS_PAGE_SIZE || pageResult.totalCount() == 0) {
                break;
            }
            pageNo++;
        }

        return savedCount;
    }

    private Optional<String> callMoisResidentPopulationWithRetry(
            String admmCd,
            String statsYm,
            String srchToYm,
            String level,
            String regSeCd,
            int pageNo,
            int pageSize) {
        for (int attempt = 1; attempt <= MOIS_MAX_ATTEMPTS; attempt++) {
            try {
                return Optional.ofNullable(dataCollectClient.callMoisResidentPopulation(
                        admmCd,
                        statsYm,
                        srchToYm,
                        level,
                        regSeCd,
                        pageNo,
                        pageSize));
            } catch (RestClientResponseException exception) {
                if (!isRetryableMoisStatus(exception.getStatusCode().value())) {
                    throw exception;
                }
                logMoisRetry(admmCd, level, pageNo, attempt, exception);
            } catch (ResourceAccessException exception) {
                logMoisRetry(admmCd, level, pageNo, attempt, exception);
            } catch (RestClientException exception) {
                logMoisRetry(admmCd, level, pageNo, attempt, exception);
            }

            if (attempt < MOIS_MAX_ATTEMPTS) {
                sleepBeforeMoisRetry();
            }
        }

        log.warn("행안부 주민등록 인구 API 호출을 건너뜁니다. admmCd={}, lv={}, pageNo={}", admmCd, level, pageNo);
        return Optional.empty();
    }

    private boolean isRetryableMoisStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void logMoisRetry(String admmCd, String level, int pageNo, int attempt, RuntimeException exception) {
        log.warn(
                "행안부 주민등록 인구 API 호출 실패. admmCd={}, lv={}, pageNo={}, attempt={}/{}, error={}",
                admmCd,
                level,
                pageNo,
                attempt,
                MOIS_MAX_ATTEMPTS,
                exception.getMessage());
    }

    private void sleepBeforeMoisRetry() {
        try {
            Thread.sleep(MOIS_RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private MoisPageResult saveMoisResidentPopulation(
            String responseBody,
            String level,
            String regSeCd,
            String fallbackStatsYm,
            Map<String, String> areaCodeCache) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode head = findMoisHead(root);
            int totalCount = moisTotalCount(root, head);
            String resultCode = moisResultCode(root, head);
            if (isMoisNoData(resultCode, root, head)) {
                return new MoisPageResult(0, 0);
            }
            if (!isMoisSuccess(resultCode)) {
                throw new IllegalStateException("행안부 주민등록 인구 API 호출에 실패했습니다. resultCode="
                        + resultCode
                        + ", resultMsg="
                        + moisResultMessage(root, head));
            }

            JsonNode itemNode = findMoisItemNode(root);
            List<MoisResidentPopulationRow> rows = parseMoisResidentPopulationRows(
                    itemNode,
                    level,
                    regSeCd,
                    fallbackStatsYm,
                    areaCodeCache);
            rows.forEach(moisResidentPopulationMapper::upsert);
            return new MoisPageResult(rows.size(), totalCount);
        } catch (Exception exception) {
            throw new IllegalStateException("행안부 주민등록 인구 응답 저장 처리에 실패했습니다.", exception);
        }
    }

    private List<MoisResidentPopulationRow> parseMoisResidentPopulationRows(
            JsonNode itemNode,
            String level,
            String regSeCd,
            String fallbackStatsYm,
            Map<String, String> areaCodeCache) {
        List<MoisResidentPopulationRow> rows = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode rowNode : itemNode) {
                toMoisResidentPopulationRow(rowNode, level, regSeCd, fallbackStatsYm, areaCodeCache)
                        .ifPresent(rows::add);
            }
            return rows;
        }

        if (!itemNode.isMissingNode() && !itemNode.isNull()) {
            toMoisResidentPopulationRow(itemNode, level, regSeCd, fallbackStatsYm, areaCodeCache)
                    .ifPresent(rows::add);
        }
        return rows;
    }

    private java.util.Optional<MoisResidentPopulationRow> toMoisResidentPopulationRow(
            JsonNode rowNode,
            String level,
            String regSeCd,
            String fallbackStatsYm,
            Map<String, String> areaCodeCache) {
        String admmCd = text(rowNode, "admmCd");
        if (admmCd.isBlank()) {
            return java.util.Optional.empty();
        }

        String statsYm = text(rowNode, "statsYm");
        if (statsYm.isBlank()) {
            statsYm = fallbackStatsYm;
        }

        String ctpvNm = text(rowNode, "ctpvNm");
        String sggNm = text(rowNode, "sggNm");
        String dongNm = text(rowNode, "dongNm");
        String areaLevel = areaLevelByMoisLevel(level);
        String areaCode = areaCodeCache.computeIfAbsent(
                admmCd + "|" + ctpvNm + "|" + sggNm + "|" + dongNm,
                ignored -> findAreaCode(admmCd, ctpvNm, sggNm, dongNm, areaLevel));
        return java.util.Optional.of(new MoisResidentPopulationRow(
                blankToNull(areaCode),
                admmCd,
                areaLevel,
                level,
                regSeCd,
                statsYm,
                blankToNull(ctpvNm),
                blankToNull(sggNm),
                blankToNull(dongNm),
                blankToNull(text(rowNode, "tong")),
                blankToNull(text(rowNode, "ban")),
                longValue(rowNode, "totNmprCnt"),
                longValue(rowNode, "maleNmprCnt"),
                longValue(rowNode, "femlNmprCnt"),
                longValue(rowNode, "male0AgeNmprCnt"),
                longValue(rowNode, "male10AgeNmprCnt"),
                longValue(rowNode, "male20AgeNmprCnt"),
                longValue(rowNode, "male30AgeNmprCnt"),
                longValue(rowNode, "male40AgeNmprCnt"),
                longValue(rowNode, "male50AgeNmprCnt"),
                longValue(rowNode, "male60AgeNmprCnt"),
                longValue(rowNode, "male70AgeNmprCnt"),
                longValue(rowNode, "male80AgeNmprCnt"),
                longValue(rowNode, "male90AgeNmprCnt"),
                longValue(rowNode, "male100AgeNmprCnt"),
                longValue(rowNode, "feml0AgeNmprCnt"),
                longValue(rowNode, "feml10AgeNmprCnt"),
                longValue(rowNode, "feml20AgeNmprCnt"),
                longValue(rowNode, "feml30AgeNmprCnt"),
                longValue(rowNode, "feml40AgeNmprCnt"),
                longValue(rowNode, "feml50AgeNmprCnt"),
                longValue(rowNode, "feml60AgeNmprCnt"),
                longValue(rowNode, "feml70AgeNmprCnt"),
                longValue(rowNode, "feml80AgeNmprCnt"),
                longValue(rowNode, "feml90AgeNmprCnt"),
                longValue(rowNode, "feml100AgeNmprCnt"),
                rowNode.toString()));
    }

    private String findAreaCode(String admmCd, String ctpvNm, String sggNm, String dongNm, String areaLevel) {
        String areaCode = moisResidentPopulationMapper.findAreaCodeByAdmmCd(admmCd);
        if (areaCode != null && !areaCode.isBlank()) {
            return areaCode;
        }

        areaCode = moisResidentPopulationMapper.findAreaCodeByAdmmCodeAndSourceNames(
                blankToNull(admmCd),
                blankToNull(ctpvNm),
                blankToNull(sggNm),
                blankToNull(dongNm),
                areaLevel);
        if (areaCode != null && !areaCode.isBlank()) {
            return areaCode;
        }

        return moisResidentPopulationMapper.findAreaCodeBySourceNames(
                blankToNull(ctpvNm),
                blankToNull(sggNm),
                blankToNull(dongNm),
                areaLevel);
    }

    private JsonNode firstNode(JsonNode node) {
        if (node.isArray() && !node.isEmpty()) {
            return node.get(0);
        }
        return node;
    }

    private JsonNode findMoisHead(JsonNode root) {
        JsonNode topLevelHead = root.path("head");
        if (!topLevelHead.isMissingNode()) {
            return firstNode(topLevelHead);
        }

        JsonNode upperResponseHeader = root.path("Response").path("head");
        if (!upperResponseHeader.isMissingNode()) {
            return upperResponseHeader;
        }

        JsonNode responseHeader = root.path("response").path("header");
        if (!responseHeader.isMissingNode()) {
            return responseHeader;
        }

        JsonNode responseBody = root.path("response").path("body");
        if (!responseBody.isMissingNode()) {
            return responseBody;
        }

        java.util.Iterator<JsonNode> values = root.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isArray()) {
                continue;
            }
            for (JsonNode section : value) {
                JsonNode sectionHead = section.path("head");
                if (!sectionHead.isMissingNode()) {
                    return firstNode(sectionHead);
                }
            }
        }

        return root.path("__missing__");
    }

    private JsonNode findMoisItemNode(JsonNode root) {
        JsonNode topLevelItems = root.path("items").path("item");
        if (!topLevelItems.isMissingNode()) {
            return topLevelItems;
        }

        JsonNode upperResponseItems = root.path("Response").path("items").path("item");
        if (!upperResponseItems.isMissingNode()) {
            return upperResponseItems;
        }

        JsonNode responseItems = root.path("response").path("body").path("items").path("item");
        if (!responseItems.isMissingNode()) {
            return responseItems;
        }

        java.util.Iterator<JsonNode> values = root.elements();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isArray()) {
                continue;
            }
            for (JsonNode section : value) {
                JsonNode row = section.path("row");
                if (!row.isMissingNode()) {
                    return row;
                }
                JsonNode item = section.path("items").path("item");
                if (!item.isMissingNode()) {
                    return item;
                }
            }
        }

        return root.path("__missing__");
    }

    private int moisTotalCount(JsonNode root, JsonNode head) {
        int totalCount = integer(head, "totalCount");
        if (totalCount > 0) {
            return totalCount;
        }
        totalCount = integer(root.path("Response").path("head"), "totalCount");
        if (totalCount > 0) {
            return totalCount;
        }
        return integer(root.path("response").path("body"), "totalCount");
    }

    private String moisResultCode(JsonNode root, JsonNode head) {
        String resultCode = text(head, "resultCode");
        if (!resultCode.isBlank()) {
            return resultCode;
        }

        String nestedResultCode = text(head.path("RESULT"), "CODE");
        if (!nestedResultCode.isBlank()) {
            return nestedResultCode;
        }

        String upperResponseResultCode = text(root.path("Response").path("head"), "resultCode");
        if (!upperResponseResultCode.isBlank()) {
            return upperResponseResultCode;
        }

        return text(root.path("response").path("header"), "resultCode");
    }

    private boolean isMoisNoData(String resultCode, JsonNode root, JsonNode head) {
        return "3".equals(resultCode)
                || "NODATA_ERROR".equalsIgnoreCase(moisResultMessage(root, head));
    }

    private String moisResultMessage(JsonNode root, JsonNode head) {
        String resultMessage = text(head, "resultMsg");
        if (!resultMessage.isBlank()) {
            return resultMessage;
        }

        resultMessage = text(head.path("RESULT"), "MESSAGE");
        if (!resultMessage.isBlank()) {
            return resultMessage;
        }

        resultMessage = text(root.path("Response").path("head"), "resultMsg");
        if (!resultMessage.isBlank()) {
            return resultMessage;
        }

        return text(root.path("response").path("header"), "resultMsg");
    }

    private boolean isMoisSuccess(String resultCode) {
        return resultCode == null
                || resultCode.isBlank()
                || "0".equals(resultCode)
                || "00".equals(resultCode)
                || "INFO-000".equals(resultCode);
    }

    private String resolveStatsYm(String statsYm) {
        if (statsYm == null || statsYm.isBlank()) {
            return YearMonth.now(SEOUL_ZONE).minusMonths(1).format(STATS_YM);
        }
        String normalized = statsYm.replace("-", "").trim();
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("statsYm은 YYYYMM 형식이어야 합니다.");
        }
        return normalized;
    }

    private String resolveMoisRegSeCd(String regSeCd) {
        if (regSeCd == null || regSeCd.isBlank()) {
            return MOIS_DEFAULT_REG_SE_CD;
        }
        String normalized = regSeCd.trim();
        if (!normalized.matches("[1-4]")) {
            throw new IllegalArgumentException("regSeCd는 1, 2, 3, 4 중 하나여야 합니다.");
        }
        return normalized;
    }

    private List<String> resolveMoisLevels(String lv) {
        if (lv == null || lv.isBlank() || "ALL".equalsIgnoreCase(lv.trim())) {
            return List.of("1", "2", "3", "4");
        }

        LinkedHashSet<String> levels = new LinkedHashSet<>();
        for (String token : lv.split(",")) {
            String level = normalizeMoisLevelToken(token);
            if (level != null) {
                levels.add(level);
            }
        }
        return new ArrayList<>(levels);
    }

    private String normalizeMoisLevelToken(String token) {
        String normalized = token == null ? "" : token.trim().toUpperCase();
        return switch (normalized) {
            case "1", "SIDO", "광역시도" -> "1";
            case "2", "SIGUNGU", "시군구" -> "2";
            case "3", "EUPMYEONDONG", "읍면동" -> "3";
            case "4", "TONG_BAN", "통반", "읍면동 통반" -> "4";
            case "TOTAL", "전체" -> null;
            default -> throw new IllegalArgumentException("lv는 1,2,3,4,ALL,TOTAL 중 하나여야 합니다.");
        };
    }

    private boolean shouldIncludeTotal(String lv) {
        if (lv == null || lv.isBlank() || "ALL".equalsIgnoreCase(lv.trim())) {
            return true;
        }
        for (String token : lv.split(",")) {
            String normalized = token.trim().toUpperCase();
            if ("TOTAL".equals(normalized) || "전체".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String areaLevelByMoisLevel(String level) {
        return switch (level) {
            case "1", "5" -> "SIDO";
            case "2", "6" -> "SIGUNGU";
            case "3", "7" -> "EUPMYEONDONG";
            case "4" -> "TONG_BAN";
            default -> throw new IllegalArgumentException("지원하지 않는 MOIS lv입니다: " + level);
        };
    }

    public int collectDailySdotVisitorCount() {
        return collectSdotVisitorCount(1, 1000);
    }

    public int collectSdotVisitorCount(int start, int end) {
        validateRange(start, end);
        String responseBody = dataCollectClient.callSdotVisitorCount(start, end);
        return saveSdotVisitorCount(responseBody);
    }

    private int saveSdotVisitorCount(String responseBody) {
        if (!hasSeoulOpenApiData(responseBody)) {
            return 0;
        }

        List<SdotVisitorRow> rows = parseSdotVisitorRows(responseBody);
        rows.forEach(sdotVisitorMapper::upsert);
        return rows.size();
    }

    private List<SdotVisitorRow> parseSdotVisitorRows(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode rowsNode = root.path(SDOT_VISITOR_COUNT).path("row");
            List<SdotVisitorRow> rows = new ArrayList<>();

            if (rowsNode.isArray()) {
                for (JsonNode rowNode : rowsNode) {
                    toSdotVisitorRow(rowNode).ifPresent(rows::add);
                }
                return rows;
            }

            if (!rowsNode.isMissingNode() && !rowsNode.isNull()) {
                toSdotVisitorRow(rowsNode).ifPresent(rows::add);
            }

            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("서울 S-DoT 유동인구 응답 저장 처리에 실패했습니다.", exception);
        }
    }

    private java.util.Optional<SdotVisitorRow> toSdotVisitorRow(JsonNode rowNode) {
        String serialNo = text(rowNode, "SERIAL_NO");
        String sensingTimeText = text(rowNode, "SENSING_TIME");
        if (serialNo.isBlank() || sensingTimeText.isBlank()) {
            return java.util.Optional.empty();
        }

        LocalDateTime sensingTime = LocalDateTime.parse(sensingTimeText, SDOT_SENSING_TIME);
        String autonomousDistrict = text(rowNode, "AUTONOMOUS_DISTRICT");
        String administrativeDistrict = text(rowNode, "ADMINISTRATIVE_DISTRICT");
        String areaCode = sdotVisitorMapper.findAreaCodeBySourceNames(
                SDOT_VISITOR_COUNT,
                autonomousDistrict,
                administrativeDistrict);
        if (areaCode == null || areaCode.isBlank()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new SdotVisitorRow(
                areaCode,
                SDOT_VISITOR_COUNT,
                sensingTime.toLocalDate(),
                "%02d".formatted(sensingTime.getHour()),
                sensingTime,
                parseSdotRegisteredAt(text(rowNode, "REG_DTTM")),
                text(rowNode, "MODEL_NM"),
                serialNo,
                text(rowNode, "REGION"),
                autonomousDistrict,
                administrativeDistrict,
                integer(rowNode, "VISITOR_COUNT"),
                rowNode.toString()));
    }

    private LocalDateTime parseSdotRegisteredAt(String registeredAt) {
        if (registeredAt == null || registeredAt.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(registeredAt, SDOT_REGISTERED_TIME);
    }

    private boolean hasSeoulOpenApiData(String responseBody) {
        return responseBody != null && responseBody.contains("\"CODE\":\"INFO-000\"");
    }

    private Long longValue(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value.replace(",", ""));
    }

    private int integer(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.replace(",", ""));
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getSdotVisitorCount(int start, int end, String district, String date) {
        validateRange(start, end);

        if(district == null || district.isBlank()) {
            return dataCollectClient.callSdotVisitorCount(start, end);
        }
        String requestDate = resolveSdotDate(date);
        return dataCollectClient.callSdotVisitorCount(start, end, district, requestDate);
    }

    public String resolveSdotDate(String date){
        if(date != null && !date.isBlank()) {
            validateIsoDate(date);
            return date;
        }
        return LocalDate.now(SEOUL_ZONE)
                .minusDays(1)
                .toString();
    }

    // start, end 범위 검증
    public void validateRange(int start, int end) {
        if(start < 1) {
            throw dataCollectException.startDateException();
        }
        if(end < start) {
            throw dataCollectException.endNotLowerThanStartException();
        }
        if(end > 1000) {
            throw dataCollectException.endLimitException();
        }
    }

    public void validateIsoDate(String date) {
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw dataCollectException.IsoDateTypeException();
        }
    }

    private record MoisPageResult(int savedCount, int totalCount) {
    }
}
