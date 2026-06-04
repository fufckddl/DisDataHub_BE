package com.hub.gisdatahub.download.dto;

import lombok.Data;

@Data
public class PointRadiusSimulationSummaryDto {
    private Long datasetId;
    private String datasetTitle;
    private Integer radius;
    private Integer totalPointCount;
    private Integer hotspotCount;
    private Double averageNearbyCount;
    private Integer maxNearbyCount;
}
