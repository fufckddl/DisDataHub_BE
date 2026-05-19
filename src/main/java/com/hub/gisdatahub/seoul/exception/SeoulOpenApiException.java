package com.hub.gisdatahub.seoul.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * [Exception] SeoulOpenApiException
 * 서울 OpenAPI 호출·파싱·비즈니스 검증 실패 시 사용합니다.
 *
 * HTTP 502 Bad Gateway 로 응답 (외부 API 오류를 클라이언트에 전달).
 * 메시지에는 서울 API의 CODE:MESSAGE 또는 내부 설명이 포함됩니다.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class SeoulOpenApiException extends RuntimeException {

    public SeoulOpenApiException(String message) {
        super(message);
    }

    public SeoulOpenApiException(String message, Throwable cause) {
        super(message, cause);
    }

}
