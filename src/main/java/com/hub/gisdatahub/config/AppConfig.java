package com.hub.gisdatahub.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
// 이제 사용 안 하는 파일입니다...!
@Configuration
public class AppConfig implements WebMvcConfigurer {

    // 1. 아까 만든 FileConfig에서 temp 경로 가져오기
    @Autowired
    @Qualifier("tempFileRootPath")
    private String tempRootPath;

    // 2. 아까 만든 FileConfig에서 upload 경로 가져오기
    @Autowired
    @Qualifier("uploadFileRootPath")
    private String uploadRootPath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 프론트에서 /tempFiles/... 로 요청하면 C:/tempFiles/... 폴더를 열어줌
        registry.addResourceHandler("/tempFiles/**")
                .addResourceLocations("file:///" + tempRootPath);

        // 프론트에서 /uploadFiles/... 로 요청하면 C:/uploadFiles/... 폴더를 열어줌
        registry.addResourceHandler("/uploadFiles/**")
                .addResourceLocations("file:///" + uploadRootPath);
    }

}
