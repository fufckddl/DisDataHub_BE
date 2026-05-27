package com.hub.gisdatahub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileConfig {
    
    // 1. 임시 파일 폴더 (tempFiles)
    @Bean(name = "tempFileRootPath")
    public String tempRootPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "C:/tempFiles/";
        } else {
            return "/tempFiles/";
        }
    }

    // 2. 최종 정식 폴더 (uploadFiles)
    @Bean(name = "uploadFileRootPath")
    public String uploadRootPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "C:/uploadFiles/";
        } else {
            return "/uploadFiles/";
        }
    }
}
