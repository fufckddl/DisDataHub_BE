package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

@Data
public class DownloadDatasetSummaryDto {
    private Integer totalDatasetCount;
    private Integer todayDownloadCount;
    private Integer yesterdayDownloadCount;
    private Double downloadChangeRate;
    private Integer supportedFormatCount;
    private List<String> supportedFormats;
    private DownloadDatasetListItemDto popularDataset;
}
