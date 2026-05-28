package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class TempFeatureDto {
    
    // ==========================================
    // 1. 식별자 구역 (기본키 & 외래키)
    // ==========================================
    private Long tempId;             // (DB 자동생성) 임시 데이터 고유 번호
    private Long uploadId;           // 부모 테이블(sd_upload_log)의 고유 번호
    private Integer rowNumber;       // 엑셀/CSV 원본의 행 번호 (에러 추적용)

    // ==========================================
    // 2. 원본 데이터 구역 (CSV에서 갓 꺼낸 날 것의 데이터)
    // ==========================================
    private Double rawLongitude;     // 원본 경도 (X)
    private Double rawLatitude;      // 원본 위도 (Y)
    private String rawWkt;           // 원본 공간 텍스트 (예: POINT(127.123 37.123))
    private String rawData;          // 나머지 자잘한 속성들을 묶어버린 JSON 문자열

    // ==========================================
    // 3. 정제 데이터 구역 (자바 검증 로직을 통과한 깨끗한 데이터)
    // ==========================================
    private String featureName;      // 정제된 이름
    private String spatialType;      // 공간 타입 (POINT, POLYGON 등)
    
    // 마이바티스에서 ST_GeomFromText() 함수를 쓰기 위해 자바에서는 텍스트(WKT)로 들고 있습니다.
    private String geom4326;         

    // ==========================================
    // 4. 상태 및 에러 기록 구역
    // ==========================================
    private String validationStatus; // 검증 상태 (초기값: PENDING, 성공: VALID, 실패: INVALID)
    private String errorMessage;     // 에러가 났을 경우 기록될 사유
}