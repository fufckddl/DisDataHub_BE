package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

@Data
public class DownloadDatasetSearchOptionsDto {
    private List<String> providers;
    private List<String> fileFormats;
    private List<DownloadDatasetCategoryOptionDto> categories;
}
