package com.hub.gisdatahub.board.service;

import java.util.List;

import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;

public interface GisReportService {
    public List<GisReportDetailDto> getGisReportList();
    //사용자 Gis 오료제보 작성 코드
    public void createGisReport(GisReportCreateRequestDto requestDto);
    // 상세페이지 코드
    public GisReportDetailDto getGisReportDetail(Long postId);
}
