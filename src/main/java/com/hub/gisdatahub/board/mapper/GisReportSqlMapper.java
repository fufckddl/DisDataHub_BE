package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;

@Mapper
public interface GisReportSqlMapper {
    public List<GisReportDetailDto> findGisReportList();
    // 게시판 작성 코드
    public void insertGisReportPost(GisReportCreateRequestDto requestDto);
    // 게시판 타입 선언
    public void insertGisReportDetail(GisReportCreateRequestDto requestDto);
    // 상세페이지 
    public GisReportDetailDto findGisReportDetail(Long postId);
    // 조회수 증가
    public int increaseGisReportViewCount(Long postId);

    // 관리자 목록 조회 코드
    public List<GisReportDetailDto> findAdminGisReportList();
    // 관리자 상세페이지 조회 코드
    public GisReportDetailDto findAdminGisReportDetail(Long postId);
}
