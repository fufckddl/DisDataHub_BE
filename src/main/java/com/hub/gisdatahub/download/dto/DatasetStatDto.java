package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 통계 테이블
@Data
public class DatasetStatDto {

    private Long datasetId;
    private Integer viewCount;
    private Integer downloadCount;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
