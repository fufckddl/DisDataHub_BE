package com.hub.gisdatahub.dataset.controller;

import com.hub.gisdatahub.dataset.service.DataValidationService;
import com.hub.gisdatahub.dataset.service.DatasetService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.dataset.dto.MyUploadResponseDto;
import com.hub.gisdatahub.dataset.dto.ValidationErrorDto;
import com.hub.gisdatahub.dataset.exception.ValidationFailedException;

@RestController
@RequestMapping("/api/upload")
public class DatasetUploadController {

    private final DataValidationService dataValidationService;
    private final DatasetService datasetService;

    DatasetUploadController(DatasetService datasetService, DataValidationService dataValidationService) {
        this.datasetService = datasetService;
        this.dataValidationService = dataValidationService;
    }

    @PostMapping("/data")
    public ResponseEntity<?> uploadData(
        @ModelAttribute DatasetUploadDto dto,
        Authentication authentication
    ) {

        // 🚀 1. 보안 신분증 검사 대신, 임시로 무조건 1번 유저라고 뻥치기!
        // int userId = 1;

        try {
            System.out.println("[1층 안내데스크] 택배 도착! 작업 반장에게 넘깁니다.");

            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 세션이 만료되었거나 정보가 없습니다.");
            }
            

            int userId = Integer.parseInt((String) authentication.getPrincipal());
            System.out.println("[보안 검사 통과] 현재 데이터셋 업로드 진행 유저 (user_id): " + userId);

            int successCount = datasetService.processUploadData(dto, userId);

            return ResponseEntity.ok(String.valueOf(successCount));
            
        } catch (ValidationFailedException e) {
            System.out.println("[컨트롤러] 데이터 검증 실패로 인한 롤백 완료: " + e.getMessage());

            // 프론트엔드가 파싱하기 쉽게 JSON(Map) 형태로 묶어서 반환
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("message", e.getMessage());
            errorResponse.put("uploadId", e.getUploadId());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (RuntimeException e) {
            System.out.println("[컨트롤러] 치명적 시스템 에러: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 에러가 발생했습니다. 관리자에게 문의하세요.");
        }
    }

    @GetMapping("/errors/{uploadId}")
    public ResponseEntity<?> getValidationErrors(@PathVariable("uploadId") Long uploadId) {
        try {
            List<ValidationErrorDto> errorList = dataValidationService.getValidationErrors(uploadId);
            return ResponseEntity.ok(errorList); // JSON 배열 형태로 예쁘게 날아갑니다!
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("에러 상세 내역을 불러오는 중 오류가 발생했습니다.");
        }
    }

    // 나의 데이터 업로드 내역 조회
    @GetMapping("/my-uploads")
    public ResponseEntity<?> getMyUploadList(Authentication authentication) {

        // 1. 신분증(토큰) 검사
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        try {
            // 2. 토큰에서 유저 PK(ID) 추출
            int userId = Integer.parseInt((String) authentication.getPrincipal());

            // 3. 작업 반장(Service)에게 데이터 가져오라고 지시
            List<MyUploadResponseDto> uploadList = datasetService.getMyUploadList(userId);

            // 4. 프론트엔드로 예쁘게 포장해서 전달 (JSON 배열 형태)
            return ResponseEntity.ok(uploadList);
        } catch (NumberFormatException e) {
            System.err.println("유저 ID 파싱 에러: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("잘못된 사용자 인증 정보입니다.");
        } catch (Exception e) {
            System.err.println("업로드 내역 조회 중 서버 에러: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("내역을 불러오는 중 문제가 발생했습니다.");
        }

    }

}
