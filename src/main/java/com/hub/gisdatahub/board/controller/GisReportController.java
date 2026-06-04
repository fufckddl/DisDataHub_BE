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

import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.service.AdminAuthService;
import com.hub.gisdatahub.board.service.GisReportService;

@RestController
@RequestMapping("/api/board/gis-reports")
public class GisReportController {

    @Autowired
    public GisReportService gisReportService;

    @Autowired
    public AdminAuthService adminAuthService;

    // 사용자 GIS 오류제보 목록 조회
    @GetMapping("findGisReportList")
    public Map<String, Object> findGisReportList() {
        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> gisReportList = gisReportService.getGisReportList();

        response.put("gisReportList", gisReportList);
        response.put("result", "success");

        return response;
    }

    // 사용자 GIS 오류제보 작성
    @PostMapping("createGisReport")
    public Map<String, Object> createGisReport(@RequestBody GisReportCreateRequestDto requestDto) {
        Map<String, Object> response = new HashMap<>();

        gisReportService.createGisReport(requestDto);

        response.put("result", "success");
        response.put("postId", requestDto.getPostId());

        return response;
    }

    // 사용자 GIS 오류제보 상세 조회
    @GetMapping("{postId}")
    public Map<String, Object> findGisPostDetail(@PathVariable("postId") Long postId) {
        Map<String, Object> response = new HashMap<>();

        GisReportDetailDto gisReportDetail = gisReportService.getGisReportDetail(postId);

        response.put("gisReportDetail", gisReportDetail);
        response.put("result", "success");

        return response;
    }

    // 관리자 GIS 오류제보 목록 조회
    @GetMapping("admin/list")
    public Map<String, Object> findAdminGisReportList(Authentication authentication) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> adminGisReportList = gisReportService.getAdminGisReportList();

        response.put("adminGisReportList", adminGisReportList);
        response.put("result", "success");

        return response;
    }

    // 관리자 GIS 오류제보 상세 조회
    @GetMapping("admin/detail/{postId}")
    public Map<String, Object> findAdminGisReportDetail(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        adminAuthService.requireAdmin(authentication);

        Map<String, Object> response = new HashMap<>();

        GisReportDetailDto adminGisReportDetail = gisReportService.getAdminGisReportDetail(postId);

        response.put("adminGisReportDetail", adminGisReportDetail);
        response.put("result", "success");

        return response;
    }
}