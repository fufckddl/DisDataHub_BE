package com.hub.gisdatahub.board.service;

import java.util.List;

import com.hub.gisdatahub.board.dto.AdminGisReportProcessRequestDto;
import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.dto.GisReportProcessHistoryDto;

public interface GisReportService {
    public List<GisReportDetailDto> getGisReportList();
    //사용자 Gis 오료제보 작성 코드
    public void createGisReport(GisReportCreateRequestDto requestDto);
    // 상세페이지 코드
    public GisReportDetailDto getGisReportDetail(Long postId);

    // 관리자 목록 조회코드
    public List<GisReportDetailDto> getAdminGisReportList();
    // 관리자 상세페이지 코드
    public GisReportDetailDto getAdminGisReportDetail(Long postId);
    
    // 관리자 처리 이력 조회
    public List<GisReportProcessHistoryDto> getGisReportProcessHistoryList(Long postId);

    // 관리자 상태 변경 + 처리 내용 저장
    public void saveAdminGisReportProcess(
            Long postId,
            Integer adminUserId,
            AdminGisReportProcessRequestDto requestDto
    );

    // 관리자 삭제
    public void deleteAdminGisReport(Long postId);

    // 사용자 본인 GIS 오류제보 수정
    public void updateMyGisReport(
            Long postId,
            Integer loginUserId,
            GisReportCreateRequestDto requestDto
    );

    // 사용자 본인 GIS 오류제보 삭제
    public void deleteMyGisReport(Long postId, Integer loginUserId);
}
