package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.board.dto.AdminInquiryAnswerRequestDto;
import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;
import com.hub.gisdatahub.board.service.AdminAuthService;
import com.hub.gisdatahub.board.service.InquiryService;

@RestController
@RequestMapping("/api/board/inquiries")
public class InquiryController {

    @Autowired
    public InquiryService inquiryService;

    @Autowired
    public AdminAuthService adminAuthService;

    // 사용자 문의 목록 조회: 공개글 + 로그인 사용자의 비공개글
    @GetMapping("findInquiryList")
    public Map<String, Object> findInquiryList(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        Integer loginUserId = null;

        if (authentication != null && authentication.isAuthenticated()) {
            loginUserId = Integer.parseInt((String) authentication.getPrincipal());
        }

        List<InquiryDetailDto> inquiryList = inquiryService.getInquiryList(loginUserId);

        response.put("inquiryList", inquiryList);
        response.put("result", "success");

        return response;
    }

    // 사용자 문의 작성
    @PostMapping("createInquiry")
    public Map<String, Object> createInquiry(
            @RequestBody InquiryCreateRequestDto requestDto,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        requestDto.setUserId(loginUserId);

        Map<String, Object> response = new HashMap<>();

        inquiryService.createInquiryPost(requestDto);

        response.put("result", "success");

        return response;
    }

    // 사용자 문의 상세 조회
    @GetMapping("{postId}")
    public Map<String, Object> findInquiryDetail(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        Map<String, Object> response = new HashMap<>();

        Integer loginUserId = null;

        if (authentication != null && authentication.isAuthenticated()) {
            loginUserId = Integer.parseInt((String) authentication.getPrincipal());
        }

        InquiryDetailDto inquiryDetail =
                inquiryService.getInquiryDetail(postId, loginUserId);

        boolean isOwner = false;

        if (inquiryDetail != null && loginUserId != null && inquiryDetail.getUserId() != null) {
            isOwner = String.valueOf(loginUserId)
                    .equals(String.valueOf(inquiryDetail.getUserId()));
        }

        response.put("inquiryDetail", inquiryDetail);
        response.put("isOwner", isOwner);
        response.put("result", "success");

        return response;
    }

    // 사용자 본인 문의 수정
    @PutMapping("{postId}")
    public Map<String, Object> updateMyInquiry(
            @PathVariable("postId") Long postId,
            @RequestBody InquiryCreateRequestDto requestDto,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        Map<String, Object> response = new HashMap<>();

        inquiryService.updateMyInquiry(postId, loginUserId, requestDto);

        response.put("result", "success");

        return response;
    }

    // 사용자 본인 문의 삭제
    @DeleteMapping("{postId}")
    public Map<String, Object> deleteMyInquiry(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        Map<String, Object> response = new HashMap<>();

        inquiryService.deleteMyInquiry(postId, loginUserId);

        response.put("result", "success");

        return response;
    }

    // 관리자 문의 목록 조회
    @GetMapping("adminInquiryList")
    public Map<String, Object> adminInquiryList(Authentication authentication) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        List<InquiryDetailDto> adminInquiryList = inquiryService.getAdminInquiryList();

        response.put("adminInquiryList", adminInquiryList);
        response.put("result", "success");

        return response;
    }

    // 관리자 문의 상세 조회
    @GetMapping("adminInquiryDetail/{postId}")
    public Map<String, Object> adminInquiryDetail(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        InquiryDetailDto adminInquiryDetail = inquiryService.getAdminInquiryDetail(postId);

        response.put("adminInquiryDetail", adminInquiryDetail);
        response.put("result", "success");

        return response;
    }

    // 관리자 문의 답변 저장
    @PostMapping("{postId}/answer")
    public Map<String, Object> saveAdminInquiryAnswer(
            @PathVariable("postId") Long postId,
            @RequestBody AdminInquiryAnswerRequestDto requestDto,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Integer adminUserId = Integer.parseInt((String) authentication.getPrincipal());

        requestDto.setAdminUserId(adminUserId);

        Map<String, Object> response = new HashMap<>();

        inquiryService.saveAdminInquiryAnswer(postId, requestDto);

        response.put("result", "success");

        return response;
    }
}