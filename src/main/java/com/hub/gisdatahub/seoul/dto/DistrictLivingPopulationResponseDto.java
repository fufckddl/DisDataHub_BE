package com.hub.gisdatahub.seoul.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [DTO] DistrictLivingPopulationResponseDto
 * 자치구 생활인구 API 응답 래퍼.
 *
 * 서비스명(SPOP_LOCAL_RESD_PPLTN 등)이 JSON 최상위 키로 바뀔 수 있어
 * @JsonAnyGetter / @JsonAnySetter 로 동적 키를 직렬화합니다.
 *
 * JSON 형식 (serviceRootKey 예: SPOP_LOCAL_RESD_PPLTN):
 * {
 *   "SPOP_LOCAL_RESD_PPLTN": {
 *     "list_total_count": ...,
 *     "RESULT": { ... },
 *     "row": [ { "SIGNGU_NM": "종로구", "TOT_LVPOPUL": "...", ... }, ... ]
 *   }
 * }
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DistrictLivingPopulationResponseDto {

    /** JSON 루트 키 (설정의 district-living-population-service-name) */
    @JsonIgnore
    private String serviceRootKey;

    /** 루트 키 아래 본문 (list_total_count, RESULT, row) */
    @JsonIgnore
    private DistrictLivingPopulationBody body;

    /**
     * JSON 직렬화 시 { "SPOP_...": { body } } 형태로 출력.
     */
    @JsonAnyGetter
    public java.util.Map<String, DistrictLivingPopulationBody> anyGetter() {
        if (serviceRootKey == null || body == null) {
            return java.util.Map.of();
        }
        return java.util.Map.of(serviceRootKey, body);
    }

    /**
     * JSON 역직렬화 시 최상위 키 이름을 serviceRootKey에 저장.
     */
    @JsonAnySetter
    public void anySetter(String key, DistrictLivingPopulationBody value) {
        this.serviceRootKey = key;
        this.body = value;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DistrictLivingPopulationBody {

        private Integer list_total_count;
        private SeoulApiResultDto RESULT;
        private List<DistrictLivingPopulationRowDto> row;

    }

}
