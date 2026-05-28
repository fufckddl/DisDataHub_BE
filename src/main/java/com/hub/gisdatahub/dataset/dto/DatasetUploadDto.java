package com.hub.gisdatahub.dataset.dto;

import org.springframework.web.multipart.MultipartFile;
import lombok.Data;

@Data
public class DatasetUploadDto {
    
    // ==========================================
    // 📁 1. 필수 파일 및 시스템 명찰
    // ==========================================
    private MultipartFile file;      // 리액트에서 formData.append("file", file)로 보낸 진짜 파일
    private String formatGroup;      // "LIGHT" 또는 "HEAVY" 확장자 분류 명찰
    private Long datasetId;          // (백엔드 자동 세팅) 생성된 데이터셋 PK
    private Integer createdBy;       // (백엔드 자동 세팅) 업로드한 연구자 ID
    private Long uploadId;           // (백엔드 자동 세팅) 생성된 업로드 로그 PK

    // ==========================================
    // 🏢 2. 기본 정보 섹션 (Dataset 테이블용)
    // ==========================================
    private String title;            // 데이터셋 제목
    private String description;      // 상세 설명
    private Long categoryId;         // 카테고리 (HEALTH, TRAFFIC 등)
    private Integer originalSrid;    // 원본 좌표계 (4326, 5179 등 숫자 자동 변환)
    private String fileFormat;       // 데이터 포맷 (CSV, SHP, GEOJSON)
    private String spatialType;      // 공간 데이터 타입 (POINT, LINESTRING 등)
    private String provider;         // 제공 기관/출처
    private String sourceType;       // 수집 방식 (FILE_UPLOAD 등)
    private Boolean isPublic;        // 공개 여부 (리액트의 'true'/'false'가 Boolean으로 자동 변환됨)

    // ==========================================
    // 📊 3. 상세 메타데이터 섹션 (Metadata 테이블용)
    // ==========================================
    private String keywords;           // 검색 태그 (XML에서 TEXT[] 배열로 변환됨)
    private String dataStartDate;      // 데이터 수집 시작일 (XML에서 DATE로 변환됨)
    private String dataEndDate;        // 데이터 수집 종료일 (XML에서 DATE로 변환됨)
    private String temporalCoverage;   // 시간적 범위
    private String spatialCoverage;    // 공간적 범위
    private String updateCycle;        // 업데이트 주기
    private String license;            // 라이선스
    private String qualityGrade;       // 품질 등급
    private String qualityDescription; // 품질 상세 설명
    private String contactPerson;      // 담당자 이름
    private String contactEmail;       // 담당자 이메일
    private String extraMetadata;      // 기타 확장 메타데이터 (XML에서 JSONB로 변환됨)
    private String encoding;           // 인코딩 방식 (UTF-8, EUC-KR)

    // ==========================================
    // 🛠️ 4. 백엔드 시스템 자동 분석 필드 (파일 물리 정보용)
    // ==========================================
    private String originalFilename; // 원본 파일명 (ex: data.csv)
    private String fileExtension;    // 파일 확장자 (ex: .csv)
    private Long fileSize;           // 파일 용량(Byte) (※ BIGINT 대응을 위해 반드시 Long)
    private String mimeType;         // 파일 타입 (ex: text/csv)
    private String checksum;         // SHA-256 파일 지문 암호값

    private String storedFilename;   // UUID가 포함된 실제 저장 경로+파일명
    private String filePath;         // 최상위 저장 폴더명 (tempFiles)

    // 🚀 [추가] 공간 데이터 컬럼 매핑 필드
    private String lonColumnName;    // 경도(X) 컬럼 이름
    private String latColumnName;    // 위도(Y) 컬럼 이름
    private String wktColumnName;    // WKT 컬럼 이름
}