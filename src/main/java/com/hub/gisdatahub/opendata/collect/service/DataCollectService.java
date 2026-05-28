package com.hub.gisdatahub.opendata.collect.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.exception.DataCollectException;
import com.hub.gisdatahub.opendata.collect.client.DataCollectClient;
import com.hub.gisdatahub.opendata.collect.dto.seoul.SeoulPopulationRow;
import com.hub.gisdatahub.opendata.collect.dto.seoul.SdotVisitorRow;
import com.hub.gisdatahub.opendata.collect.mapper.SeoulPopulationMapper;
import com.hub.gisdatahub.opendata.collect.mapper.SdotVisitorMapper;

@Service
public class DataCollectService {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter SDOT_SENSING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");
    private static final DateTimeFormatter SDOT_REGISTERED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RECENT_DATA_LOOKBACK_DAYS = 7;
    private static final String LIVING_POPULATION_DONG = "SPOP_LOCAL_RESD_DONG";
    private static final String LIVING_POPULATION_SIGUNGU = "SPOP_LOCAL_RESD_JACHI";
    private static final String SDOT_VISITOR_COUNT = "IotVdata018";

    private final DataCollectException dataCollectException;
    private final DataCollectClient dataCollectClient;
    private final SeoulPopulationMapper seoulPopulationMapper;
    private final SdotVisitorMapper sdotVisitorMapper;
    private final ObjectMapper objectMapper;

    public DataCollectService(
        DataCollectException dataCollectException,
        DataCollectClient dataCollectClient,
        SeoulPopulationMapper seoulPopulationMapper,
        SdotVisitorMapper sdotVisitorMapper,
        ObjectMapper objectMapper
    ) {
        this.dataCollectException = dataCollectException;
        this.dataCollectClient = dataCollectClient;
        this.seoulPopulationMapper = seoulPopulationMapper;
        this.sdotVisitorMapper = sdotVisitorMapper;
        this.objectMapper = objectMapper;
    }

    // 스케줄러에서 하루 1회 호출하는 수집 진입점입니다.
    // OpenAPI 응답은 즉시 DB에 저장하고, 대시보드 화면은 이 DB 데이터만 조회합니다.
    public int collectDailySeoulSigunguLivingPopulation() {
        return collectSeoulSigunguLivingPopulation(null, "00");
    }

    // 스케줄러에서 하루 1회 호출하는 서울 S-DoT 유동인구 수집 진입점입니다.
    public int collectDailySdotVisitorCount() {
        return collectSdotVisitorCount(1, 1000);
    }

    // 수동 테스트 또는 재수집이 필요할 때 특정 기준일/시간으로 서울 자치구 생활인구를 일괄 수집합니다.
    public int collectSeoulSigunguLivingPopulation(String date, String hour) {
        String requestHour = resolveHour(hour);

        if (date != null && !date.isBlank()) {
            validateBasicDate(date);
            return collectSeoulSigunguLivingPopulationByDate(date, requestHour);
        }

        LocalDate today = LocalDate.now(SEOUL_ZONE);
        for (int daysAgo = 1; daysAgo <= RECENT_DATA_LOOKBACK_DAYS; daysAgo++) {
            String requestDate = today.minusDays(daysAgo).format(BASIC_DATE);
            int savedCount = collectSeoulSigunguLivingPopulationByDate(requestDate, requestHour);
            if (savedCount > 0) {
                return savedCount;
            }
        }

        return 0;
    }

    private int collectSeoulSigunguLivingPopulationByDate(String date, String hour) {
        int savedCount = 0;
        for (String sigunguCode : seoulPopulationMapper.findSeoulSigunguApiCodes()) {
            String result = dataCollectClient.callLivingPopulationBySigungu(
                date,
                hour,
                sigunguCode
            );
            savedCount += saveLivingPopulation(result, LIVING_POPULATION_SIGUNGU);
        }
        return savedCount;
    }

    public String getLivingPopulationByDong(String date, String hour, String areaCode){
        String requestHour = resolveHour(hour);

        if (date != null && !date.isBlank()) {
            validateBasicDate(date);
            String result = dataCollectClient.callLivingPopulationByDong(
                date,
                requestHour,
                areaCode
            );
            saveLivingPopulation(result, LIVING_POPULATION_DONG);
            return result;
        }

        return getLatestLivingPopulationByDong(requestHour, areaCode);
    }

    public String getLivingPopulationBySigungu(String date, String hour, String sigunguCode) {
        String requestHour = resolveHour(hour);

        if (date != null && !date.isBlank()) {
            validateBasicDate(date);
            String result = dataCollectClient.callLivingPopulationBySigungu(
                date,
                requestHour,
                sigunguCode
            );
            saveLivingPopulation(result, LIVING_POPULATION_SIGUNGU);
            return result;
        }

        return getLatestLivingPopulationBySigungu(requestHour, sigunguCode);
    }

    // 서울시 인구(행정동) 통계 가장 최근 날짜에서 값 가져옴
    // → 데이터 없으면 하루 전 조회
    // → 계속 7일 전까지 조회
    // → 처음 INFO-000이 나오는 응답 반환
    private String getLatestLivingPopulationByDong(String hour, String areaCode) {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        for (int daysAgo = 0; daysAgo <= RECENT_DATA_LOOKBACK_DAYS; daysAgo++) {
            String requestDate = today.minusDays(daysAgo).format(BASIC_DATE);
            String result = dataCollectClient.callLivingPopulationByDong(
                requestDate,
                hour,
                areaCode
            );

            if (hasSeoulOpenApiData(result)) {
                saveLivingPopulation(result, LIVING_POPULATION_DONG);
                return result;
            }
        }

        String fallbackDate = today.minusDays(RECENT_DATA_LOOKBACK_DAYS).format(BASIC_DATE);
        String result = dataCollectClient.callLivingPopulationByDong(
            fallbackDate,
            hour,
            areaCode
        );
        saveLivingPopulation(result, LIVING_POPULATION_DONG);
        return result;
    }

    // 서울시 인구(자치구) 통계 가장 최근 날짜에서 값 가져옴
    private String getLatestLivingPopulationBySigungu(String hour, String sigunguCode) {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        for (int daysAgo = 0; daysAgo <= RECENT_DATA_LOOKBACK_DAYS; daysAgo++) {
            String requestDate = today.minusDays(daysAgo).format(BASIC_DATE);
            String result = dataCollectClient.callLivingPopulationBySigungu(
                requestDate,
                hour,
                sigunguCode
            );

            if (hasSeoulOpenApiData(result)) {
                saveLivingPopulation(result, LIVING_POPULATION_SIGUNGU);
                return result;
            }
        }

        String fallbackDate = today.minusDays(RECENT_DATA_LOOKBACK_DAYS).format(BASIC_DATE);
        String result = dataCollectClient.callLivingPopulationBySigungu(
            fallbackDate,
            hour,
            sigunguCode
        );
        saveLivingPopulation(result, LIVING_POPULATION_SIGUNGU);
        return result;
    }

    private boolean hasSeoulOpenApiData(String responseBody) {
        return responseBody != null && responseBody.contains("\"CODE\":\"INFO-000\"");
    }

    private int saveLivingPopulation(String responseBody, String sourceCode) {
        if (!hasSeoulOpenApiData(responseBody)) {
            return 0;
        }

        List<SeoulPopulationRow> rows = parseLivingPopulationRows(responseBody, sourceCode);
        rows.forEach(seoulPopulationMapper::upsert);
        return rows.size();
    }

    private List<SeoulPopulationRow> parseLivingPopulationRows(String responseBody, String sourceCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode serviceNode = root.path(sourceCode);
            JsonNode rowsNode = serviceNode.path("row");
            List<SeoulPopulationRow> rows = new ArrayList<>();

            if (rowsNode.isArray()) {
                for (JsonNode rowNode : rowsNode) {
                    toLivingPopulationRow(rowNode, sourceCode)
                        .ifPresent(rows::add);
                }
                return rows;
            }

            if (!rowsNode.isMissingNode() && !rowsNode.isNull()) {
                toLivingPopulationRow(rowsNode, sourceCode)
                    .ifPresent(rows::add);
            }

            return rows;
        } catch (Exception exception) {
            throw new IllegalStateException("서울 생활인구 응답 저장 처리에 실패했습니다.", exception);
        }
    }

    private java.util.Optional<SeoulPopulationRow> toLivingPopulationRow(JsonNode rowNode, String sourceCode) {
        String apiAreaCode = text(rowNode, "ADSTRD_CODE_SE");
        String storeAreaCode = resolveStoreAreaCode(apiAreaCode);

        if (storeAreaCode == null) {
            return java.util.Optional.empty();
        }

        BigDecimal male0To9 = decimal(rowNode, "MALE_F0T9_LVPOP_CO");
        BigDecimal male10To14 = decimal(rowNode, "MALE_F10T14_LVPOP_CO");
        BigDecimal male15To19 = decimal(rowNode, "MALE_F15T19_LVPOP_CO");
        BigDecimal male20To24 = decimal(rowNode, "MALE_F20T24_LVPOP_CO");
        BigDecimal male25To29 = decimal(rowNode, "MALE_F25T29_LVPOP_CO");
        BigDecimal male30To34 = decimal(rowNode, "MALE_F30T34_LVPOP_CO");
        BigDecimal male35To39 = decimal(rowNode, "MALE_F35T39_LVPOP_CO");
        BigDecimal male40To44 = decimal(rowNode, "MALE_F40T44_LVPOP_CO");
        BigDecimal male45To49 = decimal(rowNode, "MALE_F45T49_LVPOP_CO");
        BigDecimal male50To54 = decimal(rowNode, "MALE_F50T54_LVPOP_CO");
        BigDecimal male55To59 = decimal(rowNode, "MALE_F55T59_LVPOP_CO");
        BigDecimal male60To64 = decimal(rowNode, "MALE_F60T64_LVPOP_CO");
        BigDecimal male65To69 = decimal(rowNode, "MALE_F65T69_LVPOP_CO");
        BigDecimal male70To74 = decimal(rowNode, "MALE_F70T74_LVPOP_CO");

        BigDecimal female0To9 = decimal(rowNode, "FEMALE_F0T9_LVPOP_CO");
        BigDecimal female10To14 = decimal(rowNode, "FEMALE_F10T14_LVPOP_CO");
        BigDecimal female15To19 = decimal(rowNode, "FEMALE_F15T19_LVPOP_CO");
        BigDecimal female20To24 = decimal(rowNode, "FEMALE_F20T24_LVPOP_CO");
        BigDecimal female25To29 = decimal(rowNode, "FEMALE_F25T29_LVPOP_CO");
        BigDecimal female30To34 = decimal(rowNode, "FEMALE_F30T34_LVPOP_CO");
        BigDecimal female35To39 = decimal(rowNode, "FEMALE_F35T39_LVPOP_CO");
        BigDecimal female40To44 = decimal(rowNode, "FEMALE_F40T44_LVPOP_CO");
        BigDecimal female45To49 = decimal(rowNode, "FEMALE_F45T49_LVPOP_CO");
        BigDecimal female50To54 = decimal(rowNode, "FEMALE_F50T54_LVPOP_CO");
        BigDecimal female55To59 = decimal(rowNode, "FEMALE_F55T59_LVPOP_CO");
        BigDecimal female60To64 = decimal(rowNode, "FEMALE_F60T64_LVPOP_CO");
        BigDecimal female65To69 = decimal(rowNode, "FEMALE_F65T69_LVPOP_CO");
        BigDecimal female70To74 = decimal(rowNode, "FEMALE_F70T74_LVPOP_CO");

        return java.util.Optional.of(new SeoulPopulationRow(
            storeAreaCode,
            sourceCode,
            LocalDate.parse(text(rowNode, "STDR_DE_ID"), BASIC_DATE),
            text(rowNode, "TMZON_PD_SE"),
            decimal(rowNode, "TOT_LVPOP_CO"),
            sum(
                male0To9, male10To14, male15To19, male20To24, male25To29, male30To34, male35To39,
                male40To44, male45To49, male50To54, male55To59, male60To64, male65To69, male70To74
            ),
            sum(
                female0To9, female10To14, female15To19, female20To24, female25To29, female30To34, female35To39,
                female40To44, female45To49, female50To54, female55To59, female60To64, female65To69, female70To74
            ),
            male0To9,
            male10To14,
            male15To19,
            male20To24,
            male25To29,
            male30To34,
            male35To39,
            male40To44,
            male45To49,
            male50To54,
            male55To59,
            male60To64,
            male65To69,
            male70To74,
            female0To9,
            female10To14,
            female15To19,
            female20To24,
            female25To29,
            female30To34,
            female35To39,
            female40To44,
            female45To49,
            female50To54,
            female55To59,
            female60To64,
            female65To69,
            female70To74,
            rowNode.toString()
        ));
    }

    private String resolveStoreAreaCode(String apiAreaCode) {
        if (apiAreaCode == null || apiAreaCode.isBlank()) {
            return null;
        }

        String normalizedAreaCode = apiAreaCode.trim();
        String mappedAreaCode = seoulPopulationMapper.findAreaCodeByLivingPopulationApiCode(normalizedAreaCode);

        if (mappedAreaCode != null && !mappedAreaCode.isBlank()) {
            return mappedAreaCode;
        }

        return seoulPopulationMapper.existsAreaCode(normalizedAreaCode) ? normalizedAreaCode : null;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                result = result.add(value);
            }
        }
        return result;
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

    private int integer(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return 0;
        }

        return Integer.parseInt(value);
    }

    public String getSdotVisitorCount(int start, int end, String district, String date) {
        validateRange(start, end);

        if(district == null || district.isBlank()) {
            return dataCollectClient.callSdotVisitorCount(start, end);
        }
        String requestDate = resolveSdotDate(date);
        return dataCollectClient.callSdotVisitorCount(start, end, district, requestDate);
    }

    public String resolveDate(String date) {
        if(date != null && !date.isBlank()){
            validateBasicDate(date);
            return date;
        }
        return LocalDate.now(SEOUL_ZONE)
            .minusDays(1)
            .format(BASIC_DATE);
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
    public String resolveHour(String hour) {
        if(hour == null || hour.isBlank()) {
            return "00";
        }
        String normalizedHour = hour.length() == 1 ? "0" + hour : hour;
        if(!normalizedHour.matches("\\d{2}")) {
            throw dataCollectException.hourFormatException();
        }
        
        int hourValue = Integer.parseInt(normalizedHour);
        if(hourValue < 0 || hourValue > 23) {
            throw dataCollectException.hourRangeException();
        }
        return normalizedHour;
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

    // date 형식 검증
    public void validateBasicDate(String date){
        if(!date.matches("\\d{8}")) {
            throw dataCollectException.basicDateTypeException();
        }
    }
    public void validateIsoDate(String date) {
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw dataCollectException.IsoDateTypeException();
        }
    }

}
