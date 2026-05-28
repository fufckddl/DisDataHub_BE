package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 조회 로그 
// 있는 이유 : 조회로그를 통해서 많은 조회, 비정상적인 조회 로그 확인 등의 이유
@Data
public class DatasetViewLogDto {
    private Long viewId;
    private Long datasetId;
    private Integer userId;
    private String viewIp;
    private LocalDateTime createdAt;
}


