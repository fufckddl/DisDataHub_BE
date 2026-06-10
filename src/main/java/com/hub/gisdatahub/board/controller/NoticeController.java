package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.service.AdminAuthService;
import com.hub.gisdatahub.board.service.NoticeService;

@RestController
@RequestMapping("/api/board/notices")
public class NoticeController {

    @Autowired
    public NoticeService noticeService;

    @Autowired
    public AdminAuthService adminAuthService;

    // 사용자 공지사항 목록 조회
    @GetMapping("findNoticeList")
    public Map<String, Object> findNoticeList() {
        Map<String, Object> response = new HashMap<>();

        List<BoardPostDto> noticeList = noticeService.getNoticeList();

        response.put("noticeList", noticeList);
        response.put("result", "success");

        return response;
    }

    // 사용자 공지사항 상세 조회
    @GetMapping("{postId}")
    public Map<String, Object> findNoticeDetail(@PathVariable("postId") Long postId) {
        Map<String, Object> response = new HashMap<>();

        BoardPostDto noticeDetail = noticeService.getNoticeDetailData(postId);

        Long previousPostId = null;
        Long nextPostId = null;

        if (noticeDetail != null) {
            previousPostId = noticeService.getPreviousNoticePostId(postId);
            nextPostId = noticeService.getNextNoticePostId(postId);
        }

        response.put("noticeDetail", noticeDetail);
        response.put("previousPostId", previousPostId);
        response.put("nextPostId", nextPostId);
        response.put("result", "success");

        return response;
    }

    // 관리자 공지사항 작성
    @PostMapping("createNotice")
    public Map<String, Object> createNotice(
            @RequestBody BoardPostDto boardPostDto,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        noticeService.createNoticePost(boardPostDto);

        response.put("result", "success");
        response.put("postId", boardPostDto.getPostId());

        return response;
    }

    // 관리자 공지사항 목록 조회
    @GetMapping("adminNoticeList")
    public Map<String, Object> adminNoticeList(Authentication authentication) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        List<BoardPostDto> adminNotice = noticeService.getAdminNoticeList();

        response.put("result", "success");
        response.put("adminNotice", adminNotice);

        return response;
    }

    // 관리자 공지사항 상세 조회
    @GetMapping("adminNoticeDetail/{postId}")
    public Map<String, Object> adminNoticeDetailPage(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        BoardPostDto adminNoticeDetail = noticeService.getAdminNoticeDetailData(postId);

        Long previousPostId = null;
        Long nextPostId = null;

        if (adminNoticeDetail != null) {
            previousPostId = noticeService.getPreviousAdminNoticePostId(postId);
            nextPostId = noticeService.getNextAdminNoticePostId(postId);
        }

        response.put("adminNoticeDetail", adminNoticeDetail);
        response.put("previousPostId", previousPostId);
        response.put("nextPostId", nextPostId);
        response.put("result", "success");

        return response;
    }

    // 관리자 공지사항 수정
    @PutMapping("{postId}")
    public Map<String, Object> updateNoticePost(
            @PathVariable("postId") Long postId,
            @RequestBody BoardPostDto boardPostDto,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        noticeService.updateNoticePost(postId, boardPostDto);

        response.put("result", "success");

        return response;
    }

    // 관리자 공지사항 삭제
    @DeleteMapping("{postId}")
    public Map<String, Object> deleteNoticePost(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        noticeService.deleteNoticePost(postId);

        response.put("result", "success");

        return response;
    }


    @PatchMapping("/{postId}/restore")
    public Map<String, Object> restoreNoticePost(@PathVariable Long postId) {
        return noticeService.restoreNoticePost(postId);
    }
}