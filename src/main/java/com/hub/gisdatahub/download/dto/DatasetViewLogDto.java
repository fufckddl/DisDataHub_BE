package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 조회 로그
@Data
public class DatasetViewLogDto {
    private Integer viewId;
    private Integer datasetId;
    private Integer userId;
    private LocalDateTime createdAt;
}
