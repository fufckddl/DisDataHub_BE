package com.hub.gisdatahub.dataset.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.dataset.dto.ValidationErrorDto;

@Mapper
public interface ValidationMapper {

    // 1. 오답 노트(sd_import_validation_error) 작성 (INSERT)
    void insertMissingValuesErrors(Long uploadId);
    void insertInvalidGeometryErrors(Long uploadId);
    void insertOutOfKoreaGeometryErrors(Long uploadId);

    // 1차: 필수값 누락 타격 (이름, raw_wkt) - 위경도 대신 WKT를 검사하도록 변경!
    void invalidateMissingValues(Long uploadId);

    // 2차: WKT 텍스트를 진짜 공간 객체(geom_4326)로 굽기 (SRID 4326 주입)
    void bakeGeometry(Long uploadId);

    // 3차: 기하학적 무결성 검증 (꼬인 선, 닫히지 않은 면 색출)
    void invalidateInvalidGeometry(Long uploadId);

    // 4차: 한국 영토 바운더리 검증 (공간 데이터 도형 기준)
    void invalidateOutOfKoreaGeometry(Long uploadId);

    // 5차: 에러 난(INVALID) 데이터가 총 몇 개인지 카운트
    int countInvalidFeatures(Long uploadId);

    // ==========================================
    // 🌟 해피패스 전용 3대장 업데이트
    // ==========================================
    // 6-1. 살아남은 PENDING 레코드들을 모두 VALID로 승급
    void markRemainingAsValid(Long uploadId);

    // 6-2. 로그 테이블 검증 성공(SUCCESS) 처리
    void updateLogValidationSuccess(Long uploadId);

    // 6-3. 데이터셋 테이블 최종 상태 관리자 승인 대기(REQUEST)로 처리
    void updateDatasetStatusToRequest(Long datasetId);


    // 새드패스(검증 실패) 롤백용 쿼리
    
    // 4-2. 관제탑(sd_upload_log) 에러 상태로 업데이트
    void updateLogValidationFailed(@Param("uploadId") Long uploadId, @Param("errorCount") int errorCount);
    
    // 4-3. 파일 메타데이터(sd_gis_dataset_file) 경로 초기화
    void clearDatasetFilePath(Long datasetId);
    
    // 4-4. 부모 테이블(sd_gis_dataset) 상태를 INVALID로 업데이트
    void updateDatasetStatusToInvalid(Long datasetId);

    // 에러 목록 조회
    List<ValidationErrorDto> selectValidationErrors(Long uploadId);
}