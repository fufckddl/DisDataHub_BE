package com.hub.gisdatahub.seoul.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [Config] SeoulOpenApiProperties
 * application.yml / .env의 seoul.open-api 설정을 Java 객체로 매핑합니다.
 *
 * 예시 (application.yml):
 *   seoul.open-api.key: ${SEOUL_OPENDATA_KEY}
 *   seoul.open-api.district-living-population-service-name: SPOP_LOCAL_RESD_PPLTN
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "seoul.open-api")
public class SeoulOpenApiProperties {

    /** 서울 OpenAPI 호스트 (기본: http://openapi.seoul.go.kr:8088) */
    private String baseUrl = "http://openapi.seoul.go.kr:8088";

    /** 인증키 (.env의 SEOUL_OPENDATA_KEY) */
    private String key;

    /** 자치구 생활인구 API 서비스명 (URL 경로에 사용) */
    private String districtLivingPopulationServiceName = "SPOP_LOCAL_RESD_PPLTN";

    /** RealtimeCityAir 조회 시작 인덱스 (보통 1) */
    private int airQualityStart = 1;

    /** RealtimeCityAir 조회 종료 인덱스 (측정소 수에 맞게, 기본 25) */
    private int airQualityEnd = 25;

    /** 생활인구 API 조회 시작 인덱스 */
    private int districtLivingPopulationStart = 1;

    /** 생활인구 API 조회 종료 인덱스 (최대 1000) */
    private int districtLivingPopulationEnd = 1000;

}
