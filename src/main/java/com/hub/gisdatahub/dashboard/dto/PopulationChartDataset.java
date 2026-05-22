package com.hub.gisdatahub.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Chart.js datasets 배열의 개별 항목을 표현합니다.
public class PopulationChartDataset {

    private String label;
    private List<BigDecimal> data;
    private String backgroundColor;
    private String borderColor;
}
