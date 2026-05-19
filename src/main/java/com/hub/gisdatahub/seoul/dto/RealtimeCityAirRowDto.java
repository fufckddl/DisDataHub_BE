package com.hub.gisdatahub.seoul.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [DTO] RealtimeCityAirRowDto
 * RealtimeCityAir.row 배열의 한 건(측정소별 대기질).
 *
 * 필드명은 서울 API JSON 키와 동일하게 대문자+언더스코어를 유지합니다.
 * @JsonAutoDetect: Lombok getter와 대문자 필드명 매핑용
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RealtimeCityAirRowDto {

    /** 측정 일시 (예: 202605181500) */
    private String MSRMT_DT;

    /** 권역명 (예: 도심권) */
    private String SAREA_NM;

    /** 측정소명 (예: 중구) */
    private String MSRSTN_NM;

    /** 미세먼지(PM10) 농도 */
    private Double PM;

    /** 초미세먼지(PM2.5) 농도 */
    private Double FPM;

    /** 오존 */
    private Double OZON;

    /** 이산화질소 */
    private Double NTDX;

    /** 일산화탄소 */
    private Double CBMX;

    /** 아황산가스 */
    private Double SPDX;

    /** 통합대기환경지수 등급 */
    private String CAI_GRD;

    /** 통합대기환경지수 */
    private Double CAI_IDX;

    /** 지표오염물질 */
    private String CRST_SBSTN;

}
