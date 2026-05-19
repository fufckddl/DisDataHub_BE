package com.hub.gisdatahub.seoul.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hub.gisdatahub.seoul.config.SeoulOpenApiProperties;
import com.hub.gisdatahub.seoul.exception.SeoulOpenApiException;

/**
 * [Client] SeoulOpenApiClient
 * 서울 열린데이터광장 OpenAPI를 호출하는 공통 HTTP 클라이언트입니다.
 *
 * 역할:
 * - 데이터 종류(대기, 생활인구 등)와 무관하게 URL을 조립하고 GET 요청을 수행
 * - 응답은 파싱하지 않고 raw JSON 문자열로 반환 (파싱은 SeoulOpenDataParser 담당)
 *
 * URL 형식:
 *   {baseUrl}/{인증키}/json/{serviceName}/{start}/{end}/{pathParam1}/{pathParam2}/.../
 *
 * 예: http://openapi.seoul.go.kr:8088/{KEY}/json/RealtimeCityAir/1/25/
 * 예: http://openapi.seoul.go.kr:8088/{KEY}/json/SPOP_LOCAL_RESD_PPLTN/1/1000/20260512/
 */
@Component
public class SeoulOpenApiClient {

    private final RestClient restClient;
    private final SeoulOpenApiProperties properties;

    public SeoulOpenApiClient(RestClient seoulOpenApiRestClient, SeoulOpenApiProperties properties) {
        this.restClient = seoulOpenApiRestClient;
        this.properties = properties;
    }

    /**
     * 서울 OpenAPI 공통 호출.
     *
     * @param serviceName API 서비스명 (예: RealtimeCityAir, SPOP_LOCAL_RESD_PPLTN)
     * @param start       페이징 시작 번호 (1부터)
     * @param end         페이징 끝 번호
     * @param pathParams  서비스별 추가 경로 파라미터 (예: 기준일자 yyyyMMdd)
     * @return 서울 API가 반환한 JSON 원문
     */
    public String callSeoulOpenApi(String serviceName, int start, int end, String... pathParams) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new SeoulOpenApiException("SEOUL_OPENDATA_KEY가 설정되지 않았습니다.");
        }

        String url = buildUrl(serviceName, start, end, pathParams);
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new SeoulOpenApiException("서울 열린데이터광장 API 호출 실패: " + serviceName, ex);
        }
    }

    /**
     * 서울 OpenAPI URL 문자열을 조립합니다.
     * pathParams가 있으면 start/end 뒤에 순서대로 붙이고, 마지막에 / 로 끝냅니다.
     */
    private String buildUrl(String serviceName, int start, int end, String... pathParams) {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        StringBuilder url = new StringBuilder(base)
                .append('/')
                .append(properties.getKey())
                .append("/json/")
                .append(serviceName)
                .append('/')
                .append(start)
                .append('/')
                .append(end);

        if (pathParams != null) {
            for (String pathParam : pathParams) {
                if (pathParam != null && !pathParam.isBlank()) {
                    url.append('/').append(pathParam);
                }
            }
        }

        url.append('/');
        return url.toString();
    }

}
