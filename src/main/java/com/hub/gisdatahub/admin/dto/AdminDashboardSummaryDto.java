package com.hub.gisdatahub.admin.dto;

import lombok.Data;

@Data
public class AdminDashboardSummaryDto {

    private Integer totalUserCount;
    private Integer totalDatasetCount;
    private Integer uploadRequestCount;
    private Integer downloadCount;

}