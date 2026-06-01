package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 상세페이지 기본 정보용 DTO
@Data
public class DownloadDatasetDetailDto {
    private Long datasetId;
    private String title;
    private String description;
    private String provider;
    private String sourceType;
    private String spatialType;
    private String fileFormat;
    private Integer originalSrid;
    private Integer storageSrid;
    private Integer analysisSrid;
    private Boolean isSpatial;
    private Boolean isPublic;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}
