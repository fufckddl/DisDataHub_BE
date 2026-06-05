package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.dto.GisReportProcessHistoryDto;

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
    // 관리자 처리 상태 변경
    public int updateAdminGisReportStatus(
            @Param("postId") Long postId,
            @Param("processStatusCode") String processStatusCode
    );

    // 관리자 처리 이력 저장
    public void insertGisReportProcessHistory(GisReportProcessHistoryDto historyDto);

    // 처리 이력 조회
    public List<GisReportProcessHistoryDto> findGisReportProcessHistoryList(Long postId);

    // 관리자 삭제
    public int deleteAdminGisReportPost(Long postId);

    // 사용자 본인 GIS 오류제보 공통 게시글 수정
    public int updateMyGisReportPost(GisReportCreateRequestDto requestDto);

    // 사용자 본인 GIS 오류제보 상세 정보 수정
    public int updateMyGisReportDetail(GisReportCreateRequestDto requestDto);

    // 사용자 본인 GIS 오류제보 삭제
    public int deleteMyGisReportPost(
        @Param("postId") Long postId,
        @Param("userId") Integer userId
    );
}
