package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

// 상세페이지에서 사용할 응답 DTO
// 상세페이지에서 각각의 api를 호출해야하는 번거로움 덜기위한 Dto
@Data
public class DatasetDownloadPageDto {
    private DownloadDatasetDetailDto dataset;
    private DatasetStatDto stats;   // 
    private DownloadDatasetFileDto sourceFile; //원본 파일
    private List<String> availableFormats; // 다운로드 가능한 형식 목록
}
