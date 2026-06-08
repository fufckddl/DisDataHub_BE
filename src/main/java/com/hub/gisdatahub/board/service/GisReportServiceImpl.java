package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.board.dto.AdminGisReportProcessRequestDto;
import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
import com.hub.gisdatahub.board.dto.GisReportProcessHistoryDto;
import com.hub.gisdatahub.board.dto.GisReportSearchRequestDto;
import com.hub.gisdatahub.board.mapper.GisReportSqlMapper;

@Service
public class GisReportServiceImpl implements GisReportService{

    @Autowired
    public GisReportSqlMapper gisReportSqlMapper;

    @Override
    public List<GisReportDetailDto> getGisReportList() {
        return gisReportSqlMapper.findGisReportList();
    }

    @Transactional
    @Override
    public void createGisReport(GisReportCreateRequestDto requestDto) {
        if (requestDto.getVisibilityStatus() == null) {
            requestDto.setVisibilityStatus("PUBLIC");
        }

        gisReportSqlMapper.insertGisReportPost(requestDto);

        gisReportSqlMapper.insertGisReportDetail(requestDto);
    }

    @Override
    public GisReportDetailDto getGisReportDetail(Long postId) {
        gisReportSqlMapper.increaseGisReportViewCount(postId);

        return gisReportSqlMapper.findGisReportDetail(postId);
    }

    @Override
    public List<GisReportDetailDto> getAdminGisReportList() {

        return gisReportSqlMapper.findAdminGisReportList();
    }

    @Override
    public GisReportDetailDto getAdminGisReportDetail(Long postId) {
        return gisReportSqlMapper.findAdminGisReportDetail(postId);
    }

    @Override
    public List<GisReportProcessHistoryDto> getGisReportProcessHistoryList(Long postId) {
        return gisReportSqlMapper.findGisReportProcessHistoryList(postId);
    }

    @Transactional
    @Override
    public void saveAdminGisReportProcess(
            Long postId,
            Integer adminUserId,
            AdminGisReportProcessRequestDto requestDto
    ) {
        String processStatusCode = requestDto.getProcessStatusCode();
        String processContent = requestDto.getProcessContent();

        if (processStatusCode == null || processStatusCode.trim().isEmpty()) {
            throw new RuntimeException("처리 상태값이 필요합니다.");
        }

        if (
            !"RECEIVED".equals(processStatusCode) &&
            !"REVIEWING".equals(processStatusCode) &&
            !"PROCESSING".equals(processStatusCode) &&
            !"COMPLETED".equals(processStatusCode)
        ) {
            throw new RuntimeException("올바르지 않은 처리 상태값입니다.");
        }

        if (processContent == null || processContent.trim().isEmpty()) {
            throw new RuntimeException("관리자 처리 내용을 입력해야 합니다.");
        }

        int updateCount = gisReportSqlMapper.updateAdminGisReportStatus(
                postId,
                processStatusCode
        );

        if (updateCount == 0) {
            throw new RuntimeException("상태를 변경할 GIS 오류제보 게시글을 찾을 수 없습니다.");
        }

        GisReportProcessHistoryDto historyDto = new GisReportProcessHistoryDto();

        historyDto.setPostId(postId);
        historyDto.setUserId(adminUserId);
        historyDto.setProcessStatusCode(processStatusCode);
        historyDto.setProcessContent(processContent);

        gisReportSqlMapper.insertGisReportProcessHistory(historyDto);
    }

    @Transactional
    @Override
    public void deleteAdminGisReport(Long postId) {
        int deleteCount = gisReportSqlMapper.deleteAdminGisReportPost(postId);

        if (deleteCount == 0) {
            throw new RuntimeException("삭제할 GIS 오류제보 게시글을 찾을 수 없습니다.");
        }
    }


    @Transactional
    @Override
    public void updateMyGisReport(
            Long postId,
            Integer loginUserId,
            GisReportCreateRequestDto requestDto
    ) {
        requestDto.setPostId(postId);
        requestDto.setUserId(loginUserId);

        if (requestDto.getVisibilityStatus() == null) {
            requestDto.setVisibilityStatus("PUBLIC");
        }

        int postUpdateCount = gisReportSqlMapper.updateMyGisReportPost(requestDto);

        if (postUpdateCount == 0) {
            throw new RuntimeException("수정 권한이 없거나 게시글을 찾을 수 없습니다.");
        }

        int detailUpdateCount = gisReportSqlMapper.updateMyGisReportDetail(requestDto);

        if (detailUpdateCount == 0) {
            throw new RuntimeException("수정할 GIS 오류제보 상세 정보를 찾을 수 없습니다.");
        }
    }

    @Transactional
    @Override
    public void deleteMyGisReport(Long postId, Integer loginUserId) {
        int deleteCount = gisReportSqlMapper.deleteMyGisReportPost(postId, loginUserId);

        if (deleteCount == 0) {
            throw new RuntimeException("삭제 권한이 없거나 게시글을 찾을 수 없습니다.");
        }
    }

    @Override
    public List<GisReportDetailDto> getGisReportSearchList(GisReportSearchRequestDto searchDto) {
        return gisReportSqlMapper.findGisReportSearchList(searchDto);
    }
}

