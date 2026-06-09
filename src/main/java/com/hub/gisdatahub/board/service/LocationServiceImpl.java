package com.hub.gisdatahub.board.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.board.dto.GeoCodeResponse;
import com.hub.gisdatahub.board.dto.LocationSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocationServiceImpl implements LocationService {

    @Value("${vworld.key}")
    private String vworldKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GeoCodeResponse geocode(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new GeoCodeResponse(
                    "fail",
                    address,
                    null,
                    null,
                    "주소가 비어 있습니다."
            );
        }

        String trimmedAddress = address.trim();

        GeoCodeResponse roadResult = requestVworldGeocode(trimmedAddress, "ROAD");

        if ("success".equals(roadResult.getResult())) {
            return roadResult;
        }

        return requestVworldGeocode(trimmedAddress, "PARCEL");
    }

    @Override
    public List<LocationSearchResponse> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String trimmedKeyword = keyword.trim();

        Map<String, LocationSearchResponse> resultMap = new LinkedHashMap<>();

        List<String> keywordList = createSearchKeywordList(trimmedKeyword);

        for (String searchKeyword : keywordList) {
            List<LocationSearchResponse> roadList =
                    requestVworldSearch(searchKeyword, "road");

            List<LocationSearchResponse> parcelList =
                    requestVworldSearch(searchKeyword, "parcel");

            addSearchResults(resultMap, roadList);
            addSearchResults(resultMap, parcelList);

            if (!resultMap.isEmpty()) {
                break;
            }
        }

        if (resultMap.isEmpty()) {
            addGeocodeFallbackResults(resultMap, keywordList);
        }

        return new ArrayList<>(resultMap.values());
    }

    private List<String> createSearchKeywordList(String keyword) {
        List<String> keywordList = new ArrayList<>();

        keywordList.add(keyword);

        boolean hasSido = keyword.startsWith("서울")
                || keyword.startsWith("서울특별시")
                || keyword.startsWith("부산")
                || keyword.startsWith("부산광역시")
                || keyword.startsWith("대구")
                || keyword.startsWith("대구광역시")
                || keyword.startsWith("인천")
                || keyword.startsWith("인천광역시")
                || keyword.startsWith("광주")
                || keyword.startsWith("광주광역시")
                || keyword.startsWith("대전")
                || keyword.startsWith("대전광역시")
                || keyword.startsWith("울산")
                || keyword.startsWith("울산광역시")
                || keyword.startsWith("세종")
                || keyword.startsWith("세종특별자치시")
                || keyword.startsWith("경기")
                || keyword.startsWith("경기도")
                || keyword.startsWith("강원")
                || keyword.startsWith("강원특별자치도")
                || keyword.startsWith("충북")
                || keyword.startsWith("충청북도")
                || keyword.startsWith("충남")
                || keyword.startsWith("충청남도")
                || keyword.startsWith("전북")
                || keyword.startsWith("전라북도")
                || keyword.startsWith("전남")
                || keyword.startsWith("전라남도")
                || keyword.startsWith("경북")
                || keyword.startsWith("경상북도")
                || keyword.startsWith("경남")
                || keyword.startsWith("경상남도")
                || keyword.startsWith("제주")
                || keyword.startsWith("제주특별자치도");

        boolean hasSigungu = keyword.contains("구 ")
                || keyword.contains("군 ")
                || keyword.contains("시 ");

        if (!hasSido && hasSigungu) {
            keywordList.add("서울특별시 " + keyword);
        }

        if (!hasSido && !hasSigungu) {
            keywordList.add("강남구 " + keyword);
            keywordList.add("서울특별시 강남구 " + keyword);
        }

        return keywordList;
    }

    private void addSearchResults(
            Map<String, LocationSearchResponse> resultMap,
            List<LocationSearchResponse> list
    ) {
        for (LocationSearchResponse item : list) {
            String key = item.getAddress() + "_" + item.getLatitude() + "_" + item.getLongitude();
            resultMap.putIfAbsent(key, item);
        }
    }

    private void addGeocodeFallbackResults(
            Map<String, LocationSearchResponse> resultMap,
            List<String> keywordList
    ) {
        for (String searchKeyword : keywordList) {
            GeoCodeResponse geoCodeResponse = geocode(searchKeyword);

            if (!"success".equals(geoCodeResponse.getResult())) {
                continue;
            }

            String address = geoCodeResponse.getAddress();
            String[] regions = parseRegion(address);

            LocationSearchResponse response = new LocationSearchResponse(
                    address,
                    address,
                    geoCodeResponse.getLatitude(),
                    geoCodeResponse.getLongitude(),
                    regions[0],
                    regions[1],
                    regions[2]
            );

            String key = response.getAddress() + "_" + response.getLatitude() + "_" + response.getLongitude();
            resultMap.putIfAbsent(key, response);

            break;
        }
    }

    private List<LocationSearchResponse> requestVworldSearch(String keyword, String category) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.vworld.kr/req/search")
                    .queryParam("service", "search")
                    .queryParam("request", "search")
                    .queryParam("version", "2.0")
                    .queryParam("crs", "EPSG:4326")
                    .queryParam("size", "10")
                    .queryParam("page", "1")
                    .queryParam("query", keyword)
                    .queryParam("type", "address")
                    .queryParam("category", category)
                    .queryParam("format", "json")
                    .queryParam("key", vworldKey)
                    .build()
                    .encode()
                    .toUri();

            String responseBody = restTemplate.getForObject(uri, String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("response");

            String status = response.path("status").asText();

            if (!"OK".equals(status)) {
                return List.of();
            }

            JsonNode items = response.path("result").path("items");

            if (!items.isArray()) {
                return List.of();
            }

            List<LocationSearchResponse> resultList = new ArrayList<>();

            for (JsonNode item : items) {
                LocationSearchResponse searchResponse = convertToLocationSearchResponse(item);

                if (searchResponse != null) {
                    resultList.add(searchResponse);
                }
            }

            return resultList;

        } catch (Exception e) {
            return List.of();
        }
    }

    private LocationSearchResponse convertToLocationSearchResponse(JsonNode item) {
        String title = cleanText(item.path("title").asText(""));

        String roadAddress = item.path("address").path("road").asText("");
        String parcelAddress = item.path("address").path("parcel").asText("");

        String address = !roadAddress.isBlank() ? roadAddress : parcelAddress;

        if (address == null || address.isBlank()) {
            return null;
        }

        if (title == null || title.isBlank()) {
            title = address;
        }

        JsonNode point = item.path("point");

        if (point.isMissingNode()
                || point.path("x").isMissingNode()
                || point.path("y").isMissingNode()) {
            return null;
        }

        double longitude = point.path("x").asDouble();
        double latitude = point.path("y").asDouble();

        String[] regions = parseRegion(address);

        return new LocationSearchResponse(
                title,
                address,
                latitude,
                longitude,
                regions[0],
                regions[1],
                regions[2]
        );
    }

    private GeoCodeResponse requestVworldGeocode(String address, String type) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.vworld.kr/req/address")
                    .queryParam("service", "address")
                    .queryParam("request", "GetCoord")
                    .queryParam("version", "2.0")
                    .queryParam("crs", "EPSG:4326")
                    .queryParam("address", address)
                    .queryParam("format", "json")
                    .queryParam("type", type)
                    .queryParam("errorformat", "json")
                    .queryParam("key", vworldKey)
                    .build()
                    .encode()
                    .toUri();

            String responseBody = restTemplate.getForObject(uri, String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return new GeoCodeResponse(
                        "fail",
                        address,
                        null,
                        null,
                        "VWorld 응답이 없습니다."
                );
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("response");

            String status = response.path("status").asText();

            if (!"OK".equals(status)) {
                String errorMessage = response.path("error").path("text").asText();

                if (errorMessage == null || errorMessage.isBlank()) {
                    if ("NOT_FOUND".equals(status)) {
                        errorMessage = "검색 결과가 없습니다.";
                    } else {
                        errorMessage = "주소 좌표 변환에 실패했습니다.";
                    }
                }

                return new GeoCodeResponse(
                        "fail",
                        address,
                        null,
                        null,
                        errorMessage
                );
            }

            JsonNode point = response.path("result").path("point");

            if (point.isMissingNode()
                    || point.path("x").isMissingNode()
                    || point.path("y").isMissingNode()) {
                return new GeoCodeResponse(
                        "fail",
                        address,
                        null,
                        null,
                        "좌표 정보가 없습니다."
                );
            }

            double longitude = point.path("x").asDouble();
            double latitude = point.path("y").asDouble();

            String refinedText = response.path("refined").path("text").asText();

            String resultAddress = address;

            if (refinedText != null && !refinedText.isBlank()) {
                resultAddress = refinedText;
            }

            return new GeoCodeResponse(
                    "success",
                    resultAddress,
                    latitude,
                    longitude,
                    null
            );

        } catch (Exception e) {
            return new GeoCodeResponse(
                    "fail",
                    address,
                    null,
                    null,
                    "좌표 변환 중 오류가 발생했습니다."
            );
        }
    }

    private String[] parseRegion(String address) {
        String sido = "";
        String sigungu = "";
        String eupmyeondong = "";

        if (address == null || address.isBlank()) {
            return new String[]{sido, sigungu, eupmyeondong};
        }

        String trimmedAddress = address.trim();

        String[] parts = trimmedAddress.split("\\s+");

        if (parts.length > 0) {
            sido = parts[0];
        }

        if (parts.length > 1) {
            sigungu = parts[1];
        }

        int openIndex = trimmedAddress.indexOf("(");
        int closeIndex = trimmedAddress.indexOf(")");

        if (openIndex >= 0 && closeIndex > openIndex) {
            String insideParentheses = trimmedAddress
                    .substring(openIndex + 1, closeIndex)
                    .trim();

            if (!insideParentheses.isBlank()) {
                eupmyeondong = insideParentheses;
                return new String[]{sido, sigungu, eupmyeondong};
            }
        }

        if (parts.length > 2) {
            String third = parts[2];

            if (third.endsWith("동")
                    || third.endsWith("읍")
                    || third.endsWith("면")
                    || third.endsWith("가")
                    || third.contains("동")
                    || third.contains("읍")
                    || third.contains("면")) {
                eupmyeondong = third;
            }
        }

        return new String[]{sido, sigungu, eupmyeondong};
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }
}