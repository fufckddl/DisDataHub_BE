package com.hub.gisdatahub.seoul.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [DTO] SeoulApiResultDto
 * 서울 OpenAPI 공통 결과 블록 (각 서비스 응답의 RESULT 필드).
 *
 * JSON 예:
 *   "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다." }
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SeoulApiResultDto {

    /** 응답 코드 (정상: INFO-000) */
    private String CODE;

    /** 응답 메시지 */
    private String MESSAGE;

}
