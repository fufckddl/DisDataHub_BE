package com.hub.gisdatahub.opendata.collect.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final RestClient dataGoKrRestClient;
    private final RestClient standardDataRestClient;
    private final RestClient calspiaRestClient;
    private final String seoulKey;
    private final String moisBaseUrl;
    private final String moisKey;
    private final String publicDataKey;
    private final String calspiaKey;

    public DataCollectClient(
        @Value("${seoul.open-api.base-url}") String seoulBaseUrl,
        @Value("${seoul.open-api.key}") String seoulKey,
        @Value("${MOIS_OPEN_API_BASE_URL:http://apis.data.go.kr}") String moisBaseUrl,
        @Value("${MOIS_OPEN_API_KEY:}") String moisKey,
        @Value("${DATA_GO_KR_OPEN_API_BASE_URL:https://apis.data.go.kr}") String dataGoKrBaseUrl,
        @Value("${STANDARD_DATA_OPEN_API_BASE_URL:https://api.data.go.kr}") String standardDataBaseUrl,
        @Value("${CALSPIA_OPEN_API_BASE_URL:https://www.calspia.go.kr}") String calspiaBaseUrl,
        @Value("${CALSPIA_OPEN_API_KEY:}") String calspiaKey
    ){
        this.seoulKey = seoulKey;
        this.moisBaseUrl = moisBaseUrl;
        this.moisKey = resolveValue(moisKey, "MOIS_OPEN_API_KEY");
        this.publicDataKey = this.moisKey;
        this.calspiaKey = resolveValue(calspiaKey, "CALSPIA_OPEN_API_KEY");
        this.seoulRestClient = RestClient.builder()
            .baseUrl(seoulBaseUrl)
            .build();
        this.moisRestClient = RestClient.builder()
            .baseUrl(moisBaseUrl)
            .requestFactory(moisRequestFactory())
            .build();
        this.dataGoKrRestClient = RestClient.builder()
            .baseUrl(dataGoKrBaseUrl)
            .requestFactory(moisRequestFactory())
            .build();
        this.standardDataRestClient = RestClient.builder()
            .baseUrl(standardDataBaseUrl)
            .requestFactory(moisRequestFactory())
            .build();
        this.calspiaRestClient = RestClient.builder()
            .baseUrl(calspiaBaseUrl)
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

        return callOpenApi(
                moisBaseUrl,
                MOIS_RESIDENT_POPULATION_PATH,
                Map.of(
                        "serviceKey", moisKey,
                        "admmCd", admmCd,
                        "srchFrYm", srchFrYm,
                        "srchToYm", srchToYm,
                        "lv", level,
                        "regSeCd", regSeCd,
                        "type", "JSON",
                        "numOfRows", numOfRows,
                        "pageNo", pageNo),
                MOIS_CONNECT_TIMEOUT,
                MOIS_READ_TIMEOUT);
    }

    public String callDataGoKrOpenApi(String path, Map<String, ?> queryParams) {
        if (publicDataKey == null || publicDataKey.isBlank()) {
            throw new IllegalStateException("MOIS_OPEN_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        return dataGoKrRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path).queryParam("serviceKey", publicDataKey);
                    queryParams.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .body(String.class);
    }

    public String callStandardDataOpenApi(String path, Map<String, ?> queryParams) {
        if (publicDataKey == null || publicDataKey.isBlank()) {
            throw new IllegalStateException("MOIS_OPEN_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        return standardDataRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path).queryParam("serviceKey", publicDataKey);
                    queryParams.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .body(String.class);
    }

    public String callCalspiaOpenApi(String path, Map<String, ?> queryParams) {
        if (calspiaKey == null || calspiaKey.isBlank()) {
            throw new IllegalStateException("CALSPIA_OPEN_API_KEY 환경변수가 설정되지 않았습니다.");
        }

        return calspiaRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path).queryParam("serviceKey", calspiaKey);
                    queryParams.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .body(String.class);
    }

    public String callOpenApi(String baseUrl, String path, Map<String, ?> queryParams) {
        return callOpenApi(baseUrl, path, queryParams, MOIS_CONNECT_TIMEOUT, MOIS_READ_TIMEOUT);
    }

    public String callOpenApi(
            String baseUrl,
            String path,
            Map<String, ?> queryParams,
            Duration connectTimeout,
            Duration readTimeout) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(openApiUri(baseUrl, path, queryParams))
                    .timeout(readTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAPI HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("OpenAPI request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAPI request interrupted", exception);
        }
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
        return requestFactory(MOIS_CONNECT_TIMEOUT, MOIS_READ_TIMEOUT);
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    private URI openApiUri(String baseUrl, String path, Map<String, ?> queryParams) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String queryString = queryParams.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encodeQueryPart(entry.getKey()) + "=" + encodeQueryPart(entry.getValue()))
                .collect(Collectors.joining("&"));
        String uri = normalizedBaseUrl + normalizedPath;
        if (!queryString.isBlank()) {
            uri += "?" + queryString;
        }
        return URI.create(uri);
    }

    private String encodeQueryPart(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }
}
