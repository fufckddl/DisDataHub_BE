package com.hub.gisdatahub.seoul.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [DTO] DistrictLivingPopulationRowDto
 * 자치구 생활인구 API row 배열의 한 건.
 *
 * 필드명은 서울 API 스펙과 동일 (성별·연령대별 생활인구 수).
 * 연령대 필드는 MALE_FxxTy_LVPOPUL / FEMALE_FxxTy_LVPOPUL 패턴입니다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class DistrictLivingPopulationRowDto {

    /** 기준일 ID (yyyyMMdd) */
    private String STDR_DE_ID;

    /** 시간대 구분 */
    private String TMZON_PD_SE;

    /** 자치구 코드 */
    private String SIGNGU_CODE;

    /** 자치구명 */
    private String SIGNGU_NM;

    /** 총 생활인구 */
    private String TOT_LVPOPUL;

    // 남성 연령대별 생활인구 (서울 API 필드명 그대로)
    private String MALE_F0T9_LVPOPUL;
    private String MALE_F10T14_LVPOPUL;
    private String MALE_F15T19_LVPOPUL;
    private String MALE_F20T24_LVPOPUL;
    private String MALE_F25T29_LVPOPUL;
    private String MALE_F30T34_LVPOPUL;
    private String MALE_F35T39_LVPOPUL;
    private String MALE_F40T44_LVPOPUL;
    private String MALE_F45T49_LVPOPUL;
    private String MALE_F50T54_LVPOPUL;
    private String MALE_F55T59_LVPOPUL;
    private String MALE_F60T64_LVPOPUL;
    private String MALE_F65T69_LVPOPUL;
    private String MALE_F70T74_LVPOPUL;
    private String MALE_F75T79_LVPOPUL;
    private String MALE_F80T84_LVPOPUL;
    private String MALE_F85T89_LVPOPUL;
    private String MALE_F90T94_LVPOPUL;
    private String MALE_F95T99_LVPOPUL;
    private String MALE_F100T_LVPOPUL;

    // 여성 연령대별 생활인구
    private String FEMALE_F0T9_LVPOPUL;
    private String FEMALE_F10T14_LVPOPUL;
    private String FEMALE_F15T19_LVPOPUL;
    private String FEMALE_F20T24_LVPOPUL;
    private String FEMALE_F25T29_LVPOPUL;
    private String FEMALE_F30T34_LVPOPUL;
    private String FEMALE_F35T39_LVPOPUL;
    private String FEMALE_F40T44_LVPOPUL;
    private String FEMALE_F45T49_LVPOPUL;
    private String FEMALE_F50T54_LVPOPUL;
    private String FEMALE_F55T59_LVPOPUL;
    private String FEMALE_F60T64_LVPOPUL;
    private String FEMALE_F65T69_LVPOPUL;
    private String FEMALE_F70T74_LVPOPUL;
    private String FEMALE_F75T79_LVPOPUL;
    private String FEMALE_F80T84_LVPOPUL;
    private String FEMALE_F85T89_LVPOPUL;
    private String FEMALE_F90T94_LVPOPUL;
    private String FEMALE_F95T99_LVPOPUL;
    private String FEMALE_F100T_LVPOPUL;

}
