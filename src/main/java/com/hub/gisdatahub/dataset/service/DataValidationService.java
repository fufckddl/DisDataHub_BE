package com.hub.gisdatahub.dataset.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.dataset.dto.ValidationErrorDto;
import com.hub.gisdatahub.dataset.exception.ValidationFailedException;
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;
import com.hub.gisdatahub.dataset.mapper.ValidationMapper;

@Service
public class DataValidationService {

    @Autowired
    private ValidationMapper validationMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private FileUploadService fileUploadService;

    // 임시 테이블(sd_upload_temp_feature)에 적재된 데이터를 PostGIS 기반으로 전수 검사
    @Transactional(noRollbackFor = ValidationFailedException.class, rollbackFor = Exception.class)
    public void validateTempData(Long uploadId, Long datasetId, String storedFilename, int originalSrid) {
        System.out.println("[공간 검증반] PostGIS 공간 데이터 딥 검증을 시작합니다.");

        try {
            // 1. 필수값(이름, WKT) 검증
            validationMapper.insertMissingValuesErrors(uploadId);
            validationMapper.invalidateMissingValues(uploadId);

            // 2. 텍스트 WKT를 진짜 공간 객체(geom_4326)로 굽기 (필수 진행)
            validationMapper.bakeGeometry(uploadId, originalSrid);

            // 3. 모양이 붕괴된 도형 색출
            validationMapper.insertInvalidGeometryErrors(uploadId);
            validationMapper.invalidateInvalidGeometry(uploadId);

            // 4. 한국 영토(바운더리)를 벗어난 데이터 색출 
            validationMapper.insertOutOfKoreaGeometryErrors(uploadId);
            validationMapper.invalidateOutOfKoreaGeometry(uploadId);

        } catch (Exception e) {
            System.out.println("심각한 공간 데이터 변환 오류 발생: " + e.getMessage());
            throw new RuntimeException("공간 데이터(WKT) 변환 중 치명적 오류가 발생했습니다.");
        }

        // INVALID 처리된 에러가 몇 개인지 카운트
        int errorCount = validationMapper.countInvalidFeatures(uploadId);

        if (errorCount == 0) {
            // [해피 패스] 불량품 0개!
            System.out.println("[검증 통과] 에러 0개! 해피패스 3대장 업데이트를 시작합니다.");

            // 1. 임시 테이블: 남은 정상 데이터(PENDING)들을 모두 VALID로 변경
            validationMapper.markRemainingAsValid(uploadId);

            // 2. 로그 테이블: 이번 업로드 로그를 SUCCESS로 업데이트
            validationMapper.updateLogValidationSuccess(uploadId);

            // 3. 메인 테이블: 데이터셋의 최종 상태를 관리자 승인 대기(REQUEST)로 밀어 올리기
            validationMapper.updateDatasetStatusToRequest(datasetId);

            System.out.println("[완료] 모든 데이터가 공간 객체로 세팅되었으며, REQUEST 전환이 완료되었습니다!");
        } else {
            // [새드 패스] 에러 발견
            System.out.println("[검증 실패] 불량 데이터 " + errorCount + "개 발견! 롤백 파이프라인 가동!");

            // 1. 임시 테이블 찌꺼기 삭제 (DatasetMapper 사용)
            datasetMapper.deleteTempFeaturesByUploadId(uploadId);

            // 2. 관제탑(sd_upload_log) 에러 상태로 업데이트
            validationMapper.updateLogValidationFailed(uploadId, errorCount);

            // 3. OS 명령어: tempFiles 내부의 원본 파일 강제 삭제
            fileUploadService.deleteTempFile(storedFilename);

            // 4. 파일 메타데이터(sd_gis_dataset_file) 경로 비우기
            validationMapper.clearDatasetFilePath(datasetId);

            // 5. 부모 테이블(sd_gis_dataset) 상태 INVALID 변경
            validationMapper.updateDatasetStatusToInvalid(datasetId);

            System.out.println("[완료] 에러 내역은 보존하고, 데이터 및 파일은 안전하게 롤백(제거) 되었습니다.");

            // Controller에게 에러가 났다고 알려서 화면에 에러창을 띄우게 만듭니다.
            throw new ValidationFailedException("데이터 검증 실패: 총 " + errorCount + "개의 데이터에 오류가 있습니다.", uploadId);
        }
    }

    @Transactional(readOnly = true)
    public List<ValidationErrorDto> getValidationErrors(Long uploadId) {
        System.out.println("[검증 반장] " + uploadId + "번 업로드 건의 상세 에러 내역 조회를 시작합니다.");
        return validationMapper.selectValidationErrors(uploadId);
    }

}
