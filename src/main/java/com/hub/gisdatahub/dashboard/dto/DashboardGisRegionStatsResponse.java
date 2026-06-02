package com.hub.gisdatahub.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 지도 표시는 표본/제한 피처를 쓰되, 통계는 원천 API 전체 건수 기준으로 제공하는 응답입니다.
public class DashboardGisRegionStatsResponse {

    private String datasetCode;
    private String datasetName;
    private String metricCode;
    private String metricName;
    private LocalDate baseDate;
    private LocalDateTime collectedAt;
    private BigDecimal totalCount;
    private List<DashboardGisRegionStatItem> items;
    private String notice;
}
