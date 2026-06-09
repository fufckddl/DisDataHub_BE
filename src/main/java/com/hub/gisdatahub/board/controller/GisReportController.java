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

import com.hub.gisdatahub.board.dto.AdminGisReportProcessRequestDto;
import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.dto.GisReportProcessHistoryDto;
import com.hub.gisdatahub.board.dto.GisReportSearchRequestDto;
import com.hub.gisdatahub.board.service.AdminAuthService;
import com.hub.gisdatahub.board.service.GisReportService;

@RestController
@RequestMapping("/api/board/gis-reports")
public class GisReportController {

    @Autowired
    public GisReportService gisReportService;

    @Autowired
    public AdminAuthService adminAuthService;

    @GetMapping("findGisReportList")
    public Map<String, Object> findGisReportList() {
        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> gisReportList = gisReportService.getGisReportList();

        response.put("gisReportList", gisReportList);
        response.put("result", "success");

        return response;
    }

    @PostMapping("createGisReport")
    public Map<String, Object> createGisReport(
            @RequestBody GisReportCreateRequestDto requestDto,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        requestDto.setUserId(loginUserId);

        Map<String, Object> response = new HashMap<>();

        gisReportService.createGisReport(requestDto);

        response.put("result", "success");
        response.put("postId", requestDto.getPostId());

        return response;
    }

    @GetMapping("{postId}")
    public Map<String, Object> findGisPostDetail(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        Map<String, Object> response = new HashMap<>();

        GisReportDetailDto gisReportDetail = gisReportService.getGisReportDetail(postId);

        List<GisReportProcessHistoryDto> processHistoryList =
                gisReportService.getGisReportProcessHistoryList(postId);

        Long previousPostId = gisReportService.getPreviousGisReportPostId(postId);
        Long nextPostId = gisReportService.getNextGisReportPostId(postId);

        boolean isOwner = false;

        if (authentication != null && authentication.isAuthenticated() && gisReportDetail != null) {
            Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

            if (gisReportDetail.getUserId() != null) {
                isOwner = String.valueOf(loginUserId)
                        .equals(String.valueOf(gisReportDetail.getUserId()));
            }
        }

        response.put("gisReportDetail", gisReportDetail);
        response.put("processHistoryList", processHistoryList);
        response.put("previousPostId", previousPostId);
        response.put("nextPostId", nextPostId);
        response.put("isOwner", isOwner);
        response.put("result", "success");

        return response;
    }

    @GetMapping("admin/list")
    public Map<String, Object> findAdminGisReportList(Authentication authentication) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> adminGisReportList = gisReportService.getAdminGisReportList();

        response.put("adminGisReportList", adminGisReportList);
        response.put("result", "success");

        return response;
    }

    @GetMapping("admin/detail/{postId}")
    public Map<String, Object> findAdminGisReportDetail(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        GisReportDetailDto adminGisReportDetail =
                gisReportService.getAdminGisReportDetail(postId);

        List<GisReportProcessHistoryDto> processHistoryList =
                gisReportService.getGisReportProcessHistoryList(postId);

        Long previousPostId = gisReportService.getPreviousAdminGisReportPostId(postId);
        Long nextPostId = gisReportService.getNextAdminGisReportPostId(postId);

        response.put("previousPostId", previousPostId);
        response.put("nextPostId", nextPostId);
        response.put("adminGisReportDetail", adminGisReportDetail);
        response.put("processHistoryList", processHistoryList);
        response.put("result", "success");

        return response;
    }

    @PutMapping("admin/process/{postId}")
    public Map<String, Object> saveAdminGisReportProcess(
            @PathVariable("postId") Long postId,
            @RequestBody AdminGisReportProcessRequestDto requestDto,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Integer adminUserId = Integer.parseInt((String) authentication.getPrincipal());

        Map<String, Object> response = new HashMap<>();

        gisReportService.saveAdminGisReportProcess(postId, adminUserId, requestDto);

        response.put("result", "success");

        return response;
    }

    @DeleteMapping("admin/delete/{postId}")
    public Map<String, Object> deleteAdminGisReport(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        gisReportService.deleteAdminGisReport(postId);

        response.put("result", "success");

        return response;
    }

    @PutMapping("{postId}")
    public Map<String, Object> updateMyGisReport(
            @PathVariable("postId") Long postId,
            @RequestBody GisReportCreateRequestDto requestDto,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        Map<String, Object> response = new HashMap<>();

        gisReportService.updateMyGisReport(postId, loginUserId, requestDto);

        response.put("result", "success");

        return response;
    }

    @DeleteMapping("{postId}")
    public Map<String, Object> deleteMyGisReport(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Integer loginUserId = Integer.parseInt((String) authentication.getPrincipal());

        Map<String, Object> response = new HashMap<>();

        gisReportService.deleteMyGisReport(postId, loginUserId);

        response.put("result", "success");

        return response;
    }

    @PostMapping("search")
    public Map<String, Object> searchGisReportList(
            @RequestBody GisReportSearchRequestDto searchDto
    ) {
        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> gisReportList =
                gisReportService.getGisReportSearchList(searchDto);

        response.put("gisReportList", gisReportList);
        response.put("result", "success");

        return response;
    }
}