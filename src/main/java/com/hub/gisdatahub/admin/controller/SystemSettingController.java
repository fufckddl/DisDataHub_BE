package com.hub.gisdatahub.admin.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.service.SystemSettingService;

@RestController
public class SystemSettingController {

    @Autowired
    private SystemSettingService systemSettingService;

    @PostMapping("/api/admin/system-setting/log")
    public Map<String, Object> registerSystemSettingConfigLog(
        @RequestBody SystemSettingConfigLogDto systemSettingConfigLogDto
    ) {

        Integer result = systemSettingService.registerSystemSettingConfigLog(systemSettingConfigLogDto);

        Map<String, Object> response = new HashMap<>();

        response.put("result", result > 0);

        return response;

    }

}