package com.hub.gisdatahub.download.dto;

import lombok.Data;

@Data
public class PointRadiusSimulationSummaryDto {
    private Long datasetId;
    private String datasetTitle;
    private Integer radius;
    private String spatialType;
    private Integer totalPointCount;
    private Integer totalFeatureCount;
    private Integer hotspotCount;
    private Double averageNearbyCount;
    private Integer maxNearbyCount;
    private Integer matchedPointCount;
    private Integer matchedFeatureCount;
    private Double averageDistanceMeters;
    private Double nearestDistanceMeters;
}
