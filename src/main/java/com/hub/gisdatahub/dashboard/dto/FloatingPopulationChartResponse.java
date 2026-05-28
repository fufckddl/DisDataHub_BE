package com.hub.gisdatahub.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Chart.js에서 바로 사용할 수 있도록 유동인구 집계 결과를 가공한 응답 DTO입니다.
public class FloatingPopulationChartResponse {

    private String areaCode;
    private String areaName;
    private String fullName;
    private String areaLevel;
    private LocalDate baseDate;
    private String hour;
    private BigDecimal totalVisitorCount;
    private int rowCount;
    private int sensorCount;
    private String notice;

    private List<String> labels;
    private List<PopulationChartDataset> datasets;
    private List<FloatingPopulationRankItem> rankings;
}
