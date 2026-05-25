package com.hub.gisdatahub.download.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.download.service.DatasetDownloadService;

@RestController
@RequestMapping("/api/download")
public class DatasetDownloadController {

    private final DatasetDownloadService datasetDownloadService;

    public DatasetDownloadController(DatasetDownloadService datasetDownloadService){
        this.datasetDownloadService = datasetDownloadService;
    }

    

}
