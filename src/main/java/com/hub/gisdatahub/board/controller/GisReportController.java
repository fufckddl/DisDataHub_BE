package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.service.GisReportService;

@RestController
@RequestMapping("/api/board/gis-report")
public class GisReportController {

    @Autowired
    public GisReportService gisReportService;

    @GetMapping("findGisReportList")
    public Map<String, Object> findGisReportList() {
        Map<String, Object> response = new HashMap<>();

        List<GisReportDetailDto> gisReportList = gisReportService.getGisReportList();

        response.put("gisReportList", gisReportList);
        response.put("result", "success");

        return response;
    }
}
