package com.hub.gisdatahub.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

// 파일 목록 나열 위한 DTO
@Data
public class DownloadDatasetFileDto {
    private long fileId;
    private long datasetId;
    private String fileRole;
    private String originalFilename;
    private String storedFilename;
    private String filePath;
    private String fileExtension;
    private String fileSize;
    private String mimeType;
    private String encoding;
    private String checksum;
    private LocalDateTime createdAt;
}
