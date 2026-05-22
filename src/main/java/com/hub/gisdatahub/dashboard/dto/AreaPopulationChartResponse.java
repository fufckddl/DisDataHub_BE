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
// Chart.js에서 바로 사용할 수 있도록 labels/datasets 구조로 가공한 응답 DTO입니다.
public class AreaPopulationChartResponse {

    private String areaCode;  // 지역코드
    private String areaName; // 지역이름 
    private String fullName; // 지역 전체 이름
    private LocalDate baseDate; 
    private String hour;

    private BigDecimal totalPopulation;
    private BigDecimal malePopulation;
    private BigDecimal femalePopulation;

    // labels와 datasets는 프론트 Chart.js의 data 객체에 그대로 매핑됩니다.
    private List<String> labels;
    private List<PopulationChartDataset> datasets;

}
