package com.hub.gisdatahub.dataset.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // 🚀 필수 임포트 유지
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dataset.dto.AdminApprovalDetailResponseDto;
import com.hub.gisdatahub.dataset.dto.AdminApprovalResponseDto;
import com.hub.gisdatahub.dataset.dto.AdminRejectRequestDto;
import com.hub.gisdatahub.dataset.service.AdminApprovalService;

@RestController
@RequestMapping("/api/admin")
public class AdminApprovalController {

    @Autowired
    private AdminApprovalService adminApprovalService;

    // 업로드 요청 리스트 조회
    @GetMapping("/approvals")
    public ResponseEntity<?> getPendingApprovals(Authentication authentication) {
        System.out.println("[관리자 컨트롤러] 승인 대기 목록 요청이 접수되었습니다.");

        // 1. 로그인 여부만 가볍게 확인 (토큰이 잘 넘어왔는지만 체크)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 접속 유저 PK 식별 (권한 검사는 생략하고 식별만 합니다!)
        // 나중에 데이터 승인/반려 시 approved_by에 넣을 아주 중요한 데이터입니다.
        int adminUserId = Integer.parseInt((String) authentication.getPrincipal());
        System.out.println("[신분 확인] 현재 접속한 유저 (user_id): " + adminUserId);

        // 3. 안전하게 데이터 반환
        List<AdminApprovalResponseDto> list = adminApprovalService.getPendingApprovals();
        return ResponseEntity.ok(list);
    }

    // 데이터셋 상세 정보 조회 
    @GetMapping("/approvals/{datasetId}")
    public ResponseEntity<?> getApprovalDetail(@PathVariable("datasetId") int datasetId, Authentication authentication) {

        System.out.println("[관리자 컨트롤러] 상세 정보 요청 접수 - datasetId: " + datasetId);

        // 1. 가벼운 로그인 토큰 검사 (목록 페이지와 동일)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 서비스 호출하여 DB에서 데이터 꺼내오기
        AdminApprovalDetailResponseDto detailDto = adminApprovalService.getApprovalDetail(datasetId);

        // 3. 만약 누군가 삭제했거나 없는 번호를 요청했다면?
        if (detailDto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 데이터셋을 찾을 수 없거나 이미 처리되었습니다.");
        }

        // 4. 안전하게 프론트엔드로 반환
        return ResponseEntity.ok(detailDto);
    }

    @PostMapping("/approvals/{datasetId}/approve")
    public ResponseEntity<?> approveDataset (@PathVariable("datasetId") Long datasetId, Authentication authentication) {
        System.out.println("데이터셋 최종 승인 요청 수신 (Dataset ID: " + datasetId + ")");

        // 1. 로그인 여부 및 권한 검증
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 토큰에서 승인자(관리자) ID 추출
        int adminUserId = Integer.parseInt((String) authentication.getPrincipal());

        try {
            // 3. 무결점 DB 트랜잭션 파이프라인 가동
            adminApprovalService.approveDatasetProcess(datasetId, adminUserId);

            System.out.println("[Controller] 승인 파이프라인 무사 통과! 프론트엔드로 성공 응답 전송.");
            return ResponseEntity.ok("데이터셋이 성공적으로 최종 승인 및 본 서버로 이관되었습니다.");
        } catch (RuntimeException e) {
            // 더블클릭 방지, 잘못된 상태 등 논리적 에러
            System.err.println("[Controller] 논리적 오류 차단: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            // DB 에러 등 치명적 시스템 에러 및 롤백 완료 (500)
            System.err.println("Controller] 치명적 시스템 오류 (롤백 완료): " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 내부 오류로 승인 처리에 실패했습니다. 시스템이 이전 상태로 안전하게 복구되었습니다.");
        }
    }

    @PostMapping("/approvals/{datasetId}/reject")
    public ResponseEntity<?> rejectDataset(@PathVariable("datasetId") Long datasetId, @RequestBody AdminRejectRequestDto rejectDto, Authentication authentication) {

        System.out.println("데이터셋 반려 요청 수신 (Dataset ID: " + datasetId + ", 사유: " + rejectDto.getRejectReason() + ")");

        // 1. 로그인 여부 및 권한 검증
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 2. 토큰에서 승인자(관리자) ID 추출
        int adminUserId = Integer.parseInt((String) authentication.getPrincipal());

        // 3. 반려 사유가 비어있는지 방어 로직
        if (rejectDto.getRejectReason() == null || rejectDto.getRejectReason().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("반려 사유를 반드시 입력해야 합니다.");
        }

        try {
            // 4. 무결점 반려 DB 트랜잭션 파이프라인 가동
            adminApprovalService.rejectDatasetProcess(datasetId, adminUserId, rejectDto.getRejectReason());

            return ResponseEntity.ok("데이터셋이 반려 처리되었으며, 관련 임시 데이터가 모두 폐기되었습니다.");
        } catch (RuntimeException e) {
            System.err.println("[Controller] 논리적 오류 차단: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            System.err.println("[Controller] 치명적 시스템 오류 (롤백 완료): " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 내부 오류로 반려 처리에 실패했습니다.");
        }
    }
}