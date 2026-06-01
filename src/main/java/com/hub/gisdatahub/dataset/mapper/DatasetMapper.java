package com.hub.gisdatahub.dataset.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.dataset.dto.AdminApprovalDetailResponseDto;
import com.hub.gisdatahub.dataset.dto.AdminApprovalResponseDto;
import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.dataset.dto.MyUploadResponseDto;
import com.hub.gisdatahub.dataset.dto.TempFeatureDto;

@Mapper
public interface DatasetMapper {

    // 1. sd_gis_dataset 테이블에 기본 정보 전체 INSERT
    void insertDataset(DatasetUploadDto dto);

    // 2. sd_gis_dataset_metadata 테이블에 상세 메타데이터 전체 INSERT
    void insertMetadata(DatasetUploadDto dto);

    // 3. sd_upload_log 테이블에 초기 로그 생성
    void insertUploadLog(DatasetUploadDto dto);

    // 4. sd_gis_dataset_file 테이블에 물리 파일 정보 초기 INSERT
    void insertDatasetFile(DatasetUploadDto dto);

    // 통계 카운트 테이블
    void insertDatasetStat(Long datasetId);

    void updateFileInfo(DatasetUploadDto dto);

    // 500개의 데이터를 한 방에 넣는 Bulk Insert 메서드
    void bulkInsertTempFeatures(List<TempFeatureDto> featureList);

    // 🚀 [추가] 업로드 로그 상태 변경 (UPLOADING -> PROCESSING)
    void updateUploadStatusToProcessing(@Param("uploadId") Long uploadId, @Param("totalCount") int totalCount);

    // 파일 중복 업로드 방지를 위한 체크섬 검사
    int countByChecksum(String checksum);

    // 관리자 승인 대기 목록 조회 (status = 'REQUEST 고정')
    List<AdminApprovalResponseDto> findPendingApprovals();

    // 특정 데이터셋의 상세 정보를 가져오는 메서드
    AdminApprovalDetailResponseDto selectApprovalDetailById(int datasetId);

    // =====================================================================
    // [관리자 최종 승인 파이프라인 전용 쿼리]
    // =====================================================================

    // 0. 특정 데이터셋의 가장 최근 upload_id 찾기
    Long selectLatestUploadIdByDatasetId(Long datasetId);

    // 0-1. 특정 데이터셋의 저장된 물리 파일명(stored_filename) 조회
    String selectStoredFilenameByDatasetId(Long datasetId);

    // 1. 파일 경로 변경 (C:/tempFiles/... -> C:/uploadFiles/...)
    void updateDatasetFilePath(@Param("datasetId") Long datasetId, @Param("newFilePath") String newFilePath);

    // 2. [핵심] 임시 테이블에서 본 테이블로 데이터 고속 복사 (Bulk Insert)
    int bulkInsertFeaturesFromTemp(@Param("datasetId") Long datasetId, @Param("uploadId") Long uploadId);

    // 3. 임시 테이블 찌꺼기 삭제
    void deleteTempFeaturesByUploadId(Long uploadId);

    // 4. 업로드 로그 상태 변경 (COMPLETED)
    void updateUploadLogStatusCompleted(Long uploadId);

    // 5. 데이터셋 최종 상태 변경 (APPROVED) 및 승인자 도장 찍기
    void updateDatasetStatusApproved(@Param("datasetId") Long datasetId, @Param("adminUserId") int adminUserId);

    // 특정 데이터셋의 현재 상태(status) 조회
    String selectDatasetStatusById(Long datasetId);

    // =====================================================================
    // [관리자 최종 반려 파이프라인 전용 쿼리]
    // =====================================================================

    // 1. 반려 사유 작성 (sd_dataset_rejection)
    void insertDatasetRejection(@Param("datasetId") Long datasetId, @Param("adminUserId") int adminUserId, @Param("rejectReason") String rejectReason);

    // 2. 관제탑 로그 상태 반려 처리
    void updateUploadLogStatusRejected(Long uploadId);

    // 3. 파일 경로/이름 비우기
    void clearDatasetFileInfo(Long datasetId);

    // 4. 데이터셋 최종 상태 반려 처리
    void updateDatasetStatusRejected(Long datasetId);

    List<MyUploadResponseDto> selectMyUploadList(@Param("userId") int userId);

    // SHP, tiff 데이터 파일 전용 쿼리
    
    // 파일 검문소(SHP, TIFF)를 무사통과한 파일의 로그 상태를 성공(COMPLETE/SUCCESS)으로 업데이트
    void updateLogForDirectPass(Long uploadId);
}