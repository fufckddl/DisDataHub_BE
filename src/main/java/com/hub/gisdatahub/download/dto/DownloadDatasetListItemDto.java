package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DownloadDatasetListItemDto {
    private Long datasetId;
    private String title;
    private String description;
    private String provider;
    private Integer categoryId;
    private String categoryNameKo;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private String fileExtension;
    private Integer viewCount;
    private Integer downloadCount;
    private Boolean favorite;
}
