package com.hub.gisdatahub.dataset.service;

import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hub.gisdatahub.dataset.dto.AdminApprovalDetailResponseDto;
import com.hub.gisdatahub.dataset.dto.AdminApprovalResponseDto;
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;

@Service
public class AdminApprovalService {
    
    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @Transactional(readOnly = true)
    public List<AdminApprovalResponseDto> getPendingApprovals() {
        System.out.println("[관리자 서비스] 데이터 승인 대기 항목(REQUEST) 조회를 시작합니다.");
        return datasetMapper.findPendingApprovals();
    }

    // 특정 데이터셋 상세 정보 조회
    public AdminApprovalDetailResponseDto getApprovalDetail(int datasetId) {
        System.out.println("[Service] 데이터셋 상세 정보 조회 요청 - ID: " + datasetId);
        return datasetMapper.selectApprovalDetailById(datasetId);
    }

    // 관리자 최종 승인 트랜잭션 파이프라인
    @Transactional(rollbackFor = Exception.class)
    public void approveDatasetProcess(Long datasetId, int adminUserId) throws Exception {
        System.out.println("=================================================");
        System.out.println("[Phase 1: 데이터 조회] 승인 프로세스 가동 (Dataset ID: " + datasetId + ")");

        // 더블클릭 방어: 현재 상태가 'REQUEST'가 아니면 즉시 차단
        String currentStatus = datasetMapper.selectDatasetStatusById(datasetId);
        if (!"REQUEST".equals(currentStatus)) {
            throw new RuntimeException("이미 처리되었거나 승인 대기(REQUEST) 상태가 아닙니다.");
        }

        Long uploadId = datasetMapper.selectLatestUploadIdByDatasetId(datasetId);
        System.out.println("uploadId: " + uploadId);
        String storedFilename = datasetMapper.selectStoredFilenameByDatasetId(datasetId);

        if (uploadId == null || storedFilename == null) {
            throw new RuntimeException("승인할 데이터셋의 로그 번호나 파일 정보를 찾을 수 없습니다.");
        }

        System.out.println("[Phase 2: 파일 복사] tempFiles -> uploadFiles 복사 진행");

        // 원본 파일을 안전한 금고(uploadFiles)로 복사 (이떄 에러 나면 DB 트랜잭션 시작 전이라 그냥 튕겨 나감)
        fileUploadService.copyToUploadFolder(storedFilename);

        try {
            System.out.println("[Phase 3: DB 트랜잭션 대폭발] 고속 데이터 마이그레이션 시작");
            
            // 1. 파일 경로 메타데이트 업데이트 (DB에는 폴더명만 저장되므로 "uploadFiles" 그대로 사용)
            datasetMapper.updateDatasetFilePath(datasetId, "uploadFiles");

            // 2. 임시 테이블 -> 최종 테이블로 공간 연산(ST_Transform)하며 고속 복사
            datasetMapper.bulkInsertFeaturesFromTemp(datasetId, uploadId);

            // 3. 할 일 끝난 임시 테이블 찌꺼기 삭제
            datasetMapper.deleteTempFeaturesByUploadId(uploadId);

            // 4. 업로드 로그 상태 COMPLETED 업데이트
            datasetMapper.updateUploadLogStatusCompleted(uploadId);

            // 5. 데이터셋 상태 APPROVED (최종 승인) 및 승인자 기록
            datasetMapper.updateDatasetStatusApproved(datasetId, adminUserId);

            System.out.println("DB 업데이트 완벽 성공! 커밋(Commit)을 대기합니다.");

            // 트랜잭션 동기화, DB 커밋이 '완벽하게 끝난 직후'에만 실행되도록 원본 삭제 로직 예약
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    System.out.println("[Phase 4-A: 사후 정리] DB 커밋 100% 확인 완료! tempFiles의 원본을 영구 삭제합니다.");

                    fileUploadService.deleteTempFile(storedFilename);
                }
            });


        } catch (Exception e) {
            // 만약 Phase 3(DB 작업) 중 단 하나라도 에러가 발생한다면?
            System.out.println("[Phase 4-B: 롤백 발동] DB 에러 발생 시스템을 이전 상태로 복구합니다.");
            System.out.println("에러 사유: " + e.getMessage());

            // 1. 방금 uploadFiles에 섣불리 복사했던 파일 폐기 (원본 temp는 안전하게 살아있음)
            fileUploadService.deleteUploadFile(storedFilename);

            // 2. 스프링 부트에게 "DB 롤백시켜" 라고 알려주기 위해 예외를 다시 던짐
            throw e;
        }

    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectDatasetProcess(Long datasetId, int adminUserId, String rejectReason) throws Exception {
        System.out.println("[Phase 1: 데이터 조회] 반려 프로세스 가동 (Dataset ID: " + datasetId + ")");

        // 1. 더블 클릭 방어: 현재 상태가 'REQUEST'가 아니면 즉시 차단
        String currentStatus = datasetMapper.selectDatasetStatusById(datasetId);
        if (!"REQUEST".equals(currentStatus)) {
            throw new RuntimeException("이미 처리되었거나 승인 대기(REQUEST) 상태가 아닙니다.");
        }

        Long uploadId = datasetMapper.selectLatestUploadIdByDatasetId(datasetId);
        String storedFilename = datasetMapper.selectStoredFilenameByDatasetId(datasetId);

        if (uploadId == null || storedFilename == null) {
            throw new RuntimeException("반려할 데이터셋의 로그 번호나 파일 정보를 찾을 수 없습니다.");
        }

        System.out.println("[Phase 2: DB 롤백 및 기록 시작] 반려 사유 기록 및 데이터 찌꺼기 청소");

        try {
            // 1. 반려 사유 기록 (sd_dataset_rejection)
            datasetMapper.insertDatasetRejection(datasetId, adminUserId, rejectReason);

            // 2. 임시 테이블 찌꺼기 삭제 (기존 쿼리 완벽 재활용!)
            datasetMapper.deleteTempFeaturesByUploadId(uploadId);

            // 3. 관제탑 로그 상태 반려 처리 (REJECTED)
            datasetMapper.updateUploadLogStatusRejected(uploadId);

            // 4. 파일 메타데이터 초기화 (빈 문자열)
            datasetMapper.clearDatasetFileInfo(datasetId);

            // 5. 데이터셋 상태 최종 반려 처리 (REJECTED)
            datasetMapper.updateDatasetStatusRejected(datasetId);

            // 🚀 트랜잭션 동기화: DB 커밋이 '완벽하게 끝난 직후'에만 원본 삭제 로직 실행
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    System.out.println("[Phase 3: 물리 파일 파기] DB 커밋 100% 확인 완료! tempFiles의 원본을 영구 삭제합니다.");
                    fileUploadService.deleteTempFile(storedFilename);
                    System.out.println("반려 프로세스 완벽 성공! 데이터가 안전하게 폐기되고 사유가 기록되었습니다.");
                }
            });

        } catch (Exception e) {
            System.out.println("반려 처리 중 DB 에러 발생 (롤백 진행): " + e.getMessage());
            throw e;
        }
    }

}
