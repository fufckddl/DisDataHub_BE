package com.hub.gisdatahub.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.admin.dto.SystemSettingConfigLogDto;
import com.hub.gisdatahub.admin.dto.SystemSettingDto;
import com.hub.gisdatahub.admin.service.SystemSettingService;

@RestController
@RequestMapping("/api/admin/systemSetting")
public class SystemSettingController {

    @Autowired
    private SystemSettingService systemSettingService;

    @PostMapping("update")
    public Map<String, Object> updateSystemSetting(
        @RequestBody SystemSettingConfigLogDto systemSettingConfigLogDto
    ) {
        Map<String, Object> response = new HashMap<>();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Long adminUserId = Long.parseLong(authentication.getName());

        systemSettingConfigLogDto.setAdminUserId(adminUserId);

        systemSettingService.updateSystemSetting(systemSettingConfigLogDto);

        response.put("result", "success");

        return response;
    }

    @GetMapping("list")
    public Map<String, Object> findSystemSettingList() {
        Map<String, Object> response = new HashMap<>();

        List<SystemSettingDto> systemSettingList =
            systemSettingService.getSystemSettingList();

        response.put("systemSettingList", systemSettingList);
        response.put("result", "success");

        return response;
    }
}