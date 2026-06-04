package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    // 사용자 문의 목록 조회
    @GetMapping("findInquiryList")
    public Map<String, Object> findInquiryList() {
        Map<String, Object> response = new HashMap<>();

        List<InquiryDetailDto> inquiryList = inquiryService.getInquiryList();

        response.put("inquiryList", inquiryList);
        response.put("result", "success");

        return response;
    }

    // 사용자 문의 작성
    @PostMapping("createInquiry")
    public Map<String, Object> createInquiry(@RequestBody InquiryCreateRequestDto requestDto) {
        Map<String, Object> response = new HashMap<>();

        inquiryService.createInquiryPost(requestDto);

        response.put("result", "success");

        return response;
    }

    // 사용자 문의 상세 조회
    @GetMapping("{postId}")
    public Map<String, Object> findInquiryDetail(@PathVariable("postId") Long postId) {
        Map<String, Object> response = new HashMap<>();

        InquiryDetailDto inquiryDetail = inquiryService.getInquiryDetail(postId);

        response.put("inquiryDetail", inquiryDetail);
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

        Map<String, Object> response = new HashMap<>();

        inquiryService.saveAdminInquiryAnswer(postId, requestDto);

        response.put("result", "success");

        return response;
    }
}