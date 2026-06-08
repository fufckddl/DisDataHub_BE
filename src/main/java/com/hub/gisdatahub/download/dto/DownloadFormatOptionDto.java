package com.hub.gisdatahub.download.dto;

import lombok.Data;

@Data
public class DownloadFormatOptionDto {
    private String format;
    private Boolean available;
    private Long fileSize;
    private Boolean original;
    private String reason;
}
