package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

@Data
public class DownloadDatasetSearchResponseDto {
    private List<DownloadDatasetListItemDto> datasetList;
    private DownloadDatasetSummaryDto summary;
    private DownloadDatasetSearchOptionsDto options;
    private Integer page;
    private Integer size;
    private Integer totalCount;
    private Integer totalPages;
}
