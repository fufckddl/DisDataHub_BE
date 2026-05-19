package com.hub.gisdatahub.seoul.parser;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.seoul.dto.DistrictLivingPopulationResponseDto;
import com.hub.gisdatahub.seoul.dto.RealtimeCityAirResponseDto;
import com.hub.gisdatahub.seoul.exception.SeoulOpenApiException;

/**
 * [Parser] SeoulOpenDataParser
 * 서울 OpenAPI raw JSON → 애플리케이션 DTO 변환을 담당합니다.
 *
 * 공통 처리:
 * - RESULT.CODE 가 INFO-000 이 아니면 SeoulOpenApiException 발생
 * - 루트에 RESULT 가 없으면, 서비스 루트 객체(예: RealtimeCityAir) 안의 RESULT 를 검사
 *
 * 데이터셋별 메서드:
 * - parseAirQuality: RealtimeCityAir 키 고정 매핑
 * - parseDistrictLivingPopulation: serviceRootKey(설정값)에 따라 동적 루트 키 매핑
 */
@Component
public class SeoulOpenDataParser {

    /** 서울 API 정상 응답 코드 */
    private static final String INFO_OK = "INFO-000";

    private final ObjectMapper objectMapper;

    public SeoulOpenDataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 대기환경 JSON 파싱.
     * 입력 예: { "RealtimeCityAir": { "RESULT": {...}, "row": [ ... ] } }
     */
    public RealtimeCityAirResponseDto parseAirQuality(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            assertApiSuccess(root);

            RealtimeCityAirResponseDto response = objectMapper.readValue(rawJson, RealtimeCityAirResponseDto.class);
            if (response.getRealtimeCityAir() == null) {
                throw new SeoulOpenApiException("RealtimeCityAir 응답 본문이 없습니다.");
            }
            return response;
        } catch (SeoulOpenApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SeoulOpenApiException("대기환경 응답 파싱 실패", ex);
        }
    }

    /**
     * 자치구 생활인구 JSON 파싱.
     * 입력 예: { "SPOP_LOCAL_RESD_PPLTN": { "RESULT": {...}, "row": [ ... ] } }
     *
     * @param serviceRootKey JSON 최상위 키 (application.yml의 district-living-population-service-name 과 동일)
     */
    public DistrictLivingPopulationResponseDto parseDistrictLivingPopulation(
            String rawJson,
            String serviceRootKey) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            assertApiSuccess(root);

            JsonNode bodyNode = root.get(serviceRootKey);
            if (bodyNode == null || bodyNode.isMissingNode()) {
                throw new SeoulOpenApiException(serviceRootKey + " 응답 본문이 없습니다.");
            }

            DistrictLivingPopulationResponseDto response = new DistrictLivingPopulationResponseDto();
            response.setServiceRootKey(serviceRootKey);
            response.setBody(objectMapper.treeToValue(
                    bodyNode,
                    DistrictLivingPopulationResponseDto.DistrictLivingPopulationBody.class));
            return response;
        } catch (SeoulOpenApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SeoulOpenApiException("자치구 생활인구 응답 파싱 실패", ex);
        }
    }

    /**
     * 서울 API 오류 응답 여부를 검사합니다.
     * - 루트 RESULT (전역 오류)
     * - 또는 각 서비스 객체 내부 RESULT (정상 본문 + 오류 코드)
     */
    private void assertApiSuccess(JsonNode root) {
        JsonNode result = root.path("RESULT");
        if (!result.isMissingNode()) {
            String code = result.path("CODE").asText();
            if (!INFO_OK.equals(code)) {
                String message = result.path("MESSAGE").asText("서울 열린데이터광장 API 오류");
                throw new SeoulOpenApiException(code + ": " + message);
            }
            return;
        }

        for (JsonNode child : root) {
            if (child.isObject() && child.has("RESULT")) {
                String code = child.path("RESULT").path("CODE").asText();
                if (!INFO_OK.equals(code)) {
                    String message = child.path("RESULT").path("MESSAGE").asText("서울 열린데이터광장 API 오류");
                    throw new SeoulOpenApiException(code + ": " + message);
                }
                return;
            }
        }
    }

}
