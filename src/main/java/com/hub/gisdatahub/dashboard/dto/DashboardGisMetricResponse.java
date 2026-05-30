package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 대시보드 차트/카드/지도 툴팁에 사용할 지표 목록 응답입니다.
public class DashboardGisMetricResponse {

    private String datasetCode;
    private String metricCode;
    private String metricName;
    private String valueType;
    private String unit;
    private String chartGroup;
    private Integer sortOrder;
    private Boolean isDefault;
}
