package com.hub.gisdatahub.seoul.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * [DTO] RealtimeCityAirResponseDto
 * Controller가 React에 내려주는 대기환경 응답 래퍼.
 * 서울 API JSON 구조를 그대로 유지합니다.
 *
 * JSON 형식:
 * {
 *   "RealtimeCityAir": {
 *     "list_total_count": 25,
 *     "RESULT": { "CODE": "INFO-000", "MESSAGE": "..." },
 *     "row": [ { "MSRSTN_NM": "중구", "PM": 34.0, ... }, ... ]
 *   }
 * }
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealtimeCityAirResponseDto {

    /** 서울 API 루트 키 RealtimeCityAir 와 매핑 */
    @JsonProperty("RealtimeCityAir")
    private RealtimeCityAirBody realtimeCityAir;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RealtimeCityAirBody {

        /** 전체 건수 */
        private Integer list_total_count;

        /** API 처리 결과 */
        private SeoulApiResultDto RESULT;

        /** 측정소별 목록 */
        private List<RealtimeCityAirRowDto> row;

    }

}
