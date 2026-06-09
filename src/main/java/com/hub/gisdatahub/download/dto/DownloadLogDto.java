package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 다운로드 로그
@Data
public class DownloadLogDto {
    private Long downloadId;
    private Long datasetId;
    private Long fileId;
    private Integer userId;
    private String downloadFormat;
    private Long downloadFileSize;
    private String downloadStatus;
    private String errorMessage;
    private String downloadIp;
    private LocalDateTime createdAt;
}
