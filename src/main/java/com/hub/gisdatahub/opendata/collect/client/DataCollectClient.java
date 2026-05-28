package com.hub.gisdatahub.opendata.collect.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DataCollectClient {

    private static final String MOIS_RESIDENT_POPULATION_PATH = "/1741000/admmSexdAgePpltn/selectAdmmSexdAgePpltn";
    private static final String SDOT_VISITOR_COUNT = "IotVdata018";
    private static final Duration MOIS_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MOIS_READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient seoulRestClient;
    private final RestClient moisRestClient;
    private final String seoulKey;
    private final String moisKey;

    public DataCollectClient(
        @Value("${seoul.open-api.base-url}") String seoulBaseUrl,
        @Value("${seoul.open-api.key}") String seoulKey,
        @Value("${MOIS_OPEN_API_BASE_URL:https://apis.data.go.kr}") String moisBaseUrl,
        @Value("${MOIS_OPEN_API_KEY:}") String moisKey
    ){
        this.seoulKey = seoulKey;
        this.moisKey = resolveValue(moisKey, "MOIS_OPEN_API_KEY");
        this.seoulRestClient = RestClient.builder()
            .baseUrl(seoulBaseUrl)
            .build();
        this.moisRestClient = RestClient.builder()
            .baseUrl(moisBaseUrl)
            .requestFactory(moisRequestFactory())
            .build();
    }

    // 행정안전부 행정동별(통반단위) 성/연령별 주민등록 인구수 OpenAPI
    public String callMoisResidentPopulation(
        String admmCd,
        String srchFrYm,
        String srchToYm,
        String level,
        String regSeCd,
        int pageNo,
        int numOfRows
    ) {
        if (moisKey == null || moisKey.isBlank()) {
            throw new IllegalStateException("MOIS_OPEN_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        return moisRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(MOIS_RESIDENT_POPULATION_PATH)
                        .queryParam("serviceKey", moisKey)
                        .queryParam("admmCd", admmCd)
                        .queryParam("srchFrYm", srchFrYm)
                        .queryParam("srchToYm", srchToYm)
                        .queryParam("lv", level)
                        .queryParam("regSeCd", regSeCd)
                        .queryParam("type", "JSON")
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .body(String.class);
    }

    // 서울시 S-DoT 유동인구 측정 정보
    public String callSdotVisitorCount(
        int start,
        int end,
        String district,
        String registeredDate
    ) {
        return seoulRestClient.get()
                .uri("/{key}/json/{service}/{start}/{end}/{district}/{registeredDate}",
                    seoulKey,
                    SDOT_VISITOR_COUNT,
                    start,
                    end,
                    district,
                    registeredDate
                )
                .retrieve()
                .body(String.class);
    }

    // 서울시 S-DoT 전체 조회
    public String callSdotVisitorCount(
        int start,
        int end
    ) {
        return seoulRestClient.get()
                .uri("/{key}/json/{service}/{start}/{end}",
                    seoulKey,
                    SDOT_VISITOR_COUNT,
                    start,
                    end
                )
                .retrieve()
                .body(String.class);
    }

    private String resolveValue(String configuredValue, String envName) {
        if (configuredValue != null && !configuredValue.isBlank()) {
            return configuredValue;
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return readDotenvValue(envName);
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

    private SimpleClientHttpRequestFactory moisRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(MOIS_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(MOIS_READ_TIMEOUT);
        return requestFactory;
    }
}
