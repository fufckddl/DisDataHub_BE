package com.hub.gisdatahub.dashboard.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 전국 GIS 피처성 데이터의 시도별 통계 차트 항목입니다.
public class DashboardGisRegionStatItem {

    private String areaCode;
    private String areaName;
    private String fullName;
    private String areaLevel;
    private String sourceAreaCode;
    private BigDecimal count;
    private BigDecimal percent;
}
