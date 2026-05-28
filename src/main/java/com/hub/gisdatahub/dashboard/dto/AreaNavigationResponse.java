package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 지도 polygon 클릭 후 drill-down/drill-up에 필요한 현재 지역과 부모/자식 레벨 정보를 제공합니다.
public class AreaNavigationResponse {

    private String areaCode;
    private String areaName;
    private String fullName;
    private String areaLevel;

    private String parentAreaCode;
    private String parentAreaName;
    private String parentFullName;
    private String parentLevel;

    private String childLevel;
    private boolean canDrillDown;
}
