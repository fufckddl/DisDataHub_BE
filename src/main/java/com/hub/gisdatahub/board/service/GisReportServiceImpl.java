package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
