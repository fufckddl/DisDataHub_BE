package com.hub.gisdatahub.download.service;

import org.springframework.stereotype.Service;

import com.hub.gisdatahub.download.mapper.DatasetDownloadMapper;

@Service
public class DatasetDownloadService {
    
    private final DatasetDownloadMapper datasetDownloadMapper;
    
    public DatasetDownloadService(DatasetDownloadMapper datasetDownloadMapper){
        this.datasetDownloadMapper =  datasetDownloadMapper;
    }
}
