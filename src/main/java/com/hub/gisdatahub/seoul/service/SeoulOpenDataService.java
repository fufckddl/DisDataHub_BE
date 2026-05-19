package com.hub.gisdatahub.seoul.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.hub.gisdatahub.seoul.client.SeoulOpenApiClient;
import com.hub.gisdatahub.seoul.config.SeoulOpenApiProperties;
import com.hub.gisdatahub.seoul.dto.DistrictLivingPopulationResponseDto;
import com.hub.gisdatahub.seoul.dto.RealtimeCityAirResponseDto;
import com.hub.gisdatahub.seoul.parser.SeoulOpenDataParser;

/**
 * [Service] SeoulOpenDataService
 * 데이터셋별 비즈니스 진입점입니다. HTTP 호출·파싱 세부는 client/parser에 위임합니다.
 *
 * 공통 패턴:
 *   1) SeoulOpenApiClient.callSeoulOpenApi(...) 로 raw JSON 수신
 *   2) SeoulOpenDataParser.parse...(raw) 로 DTO 변환 후 반환
 *
 * 새 데이터 API 추가 시: 이 클래스에 getXxx() 메서드만 추가하고,
 * 서비스명·pathParams·파서 메서드를 각각 정의하면 됩니다.
 */
@Service
public class SeoulOpenDataService {

    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final SeoulOpenDataParser seoulOpenDataParser;
    private final SeoulOpenApiProperties properties;

    public SeoulOpenDataService(
            SeoulOpenApiClient seoulOpenApiClient,
            SeoulOpenDataParser seoulOpenDataParser,
            SeoulOpenApiProperties properties) {
        this.seoulOpenApiClient = seoulOpenApiClient;
        this.seoulOpenDataParser = seoulOpenDataParser;
        this.properties = properties;
    }

    /**
     * 실시간 대기환경 조회.
     * 서울 API: RealtimeCityAir / start~end (기본 1~25)
     */
    public RealtimeCityAirResponseDto getRealtimeCityAir() {
        String raw = seoulOpenApiClient.callSeoulOpenApi(
                "RealtimeCityAir",
                properties.getAirQualityStart(),
                properties.getAirQualityEnd());
        return seoulOpenDataParser.parseAirQuality(raw);
    }

    /**
     * 자치구 생활인구 조회.
     * 서울 API: {districtLivingPopulationServiceName} / start~end / {baseDate}
     */
    public DistrictLivingPopulationResponseDto getDistrictLivingPopulation(String baseDate) {
        String resolvedDate = resolveBaseDate(baseDate);
        String serviceName = properties.getDistrictLivingPopulationServiceName();
        String raw = seoulOpenApiClient.callSeoulOpenApi(
                serviceName,
                properties.getDistrictLivingPopulationStart(),
                properties.getDistrictLivingPopulationEnd(),
                resolvedDate);
        return seoulOpenDataParser.parseDistrictLivingPopulation(raw, serviceName);
    }

    /**
     * baseDate 미입력 시 오늘 기준 5일 전(yyyyMMdd).
     * 서울 OpenAPI는 최근 며칠치만 제공하는 경우가 많아 기본값을 둡니다.
     */
    private String resolveBaseDate(String baseDate) {
        if (baseDate != null && !baseDate.isBlank()) {
            return baseDate;
        }
        return LocalDate.now().minusDays(5).format(BASE_DATE);
    }

}
