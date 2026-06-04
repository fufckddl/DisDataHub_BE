package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.board.dto.GisReportCreateRequestDto;
import com.hub.gisdatahub.board.dto.GisReportDetailDto;
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
}

