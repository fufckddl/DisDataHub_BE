package com.hub.gisdatahub.download.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class DownloadAttributePreviewDto {
    private List<String> columns;
    private List<Map<String, Object>> rows;
}
