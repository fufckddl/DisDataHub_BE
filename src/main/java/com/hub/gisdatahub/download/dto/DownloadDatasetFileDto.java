package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 원본 파일 메타데이터 DTO
@Data
public class DownloadDatasetFileDto {
    private Long fileId;
    private Long datasetId;
    private String fileRole;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private String fileExtension;
    private Long fileSize;
    private String mimeType;
    private String encoding;
    private String checksum;
    private LocalDateTime createdAt;
}
