package com.hub.gisdatahub.seoul.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * [Config] SeoulOpenApiConfig
 * 서울 열린데이터광장 OpenAPI 연동에 필요한 Spring Bean을 등록합니다.
 *
 * - SeoulOpenApiProperties: application.yml의 seoul.open-api 설정 바인딩
 * - RestClient: SeoulOpenApiClient에서 HTTP GET 호출에 사용
 */
@Configuration
@EnableConfigurationProperties(SeoulOpenApiProperties.class)
public class SeoulOpenApiConfig {

    /**
     * 서울 OpenAPI 전용 RestClient.
     * 동기(blocking) 방식으로 JSON 문자열 응답을 받습니다.
     */
    @Bean
    public RestClient seoulOpenApiRestClient() {
        return RestClient.create();
    }

}
