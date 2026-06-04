package com.hub.gisdatahub.admin.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.service.AdminUserService;

@RestController
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    // 사용자 관리 유형 목록 조회
    @GetMapping("/api/admin/user-management/types")
    public Map<String, Object> getUserManagementTypeList() {

        Map<String, Object> response = new HashMap<>();

        response.put(
            "typeList",
            adminUserService.getUserManagementTypeList()
        );

        return response;

    }

    // 사용자 제재 등록
    @PostMapping("/api/admin/user-management/log")
    public Map<String, Object> registerUserManagementLog(
        @RequestBody UserManagementLogDto userManagementLogDto
    ) {

        Integer result = adminUserService.registerUserManagementLog(
            userManagementLogDto
        );

        Map<String, Object> response = new HashMap<>();

        response.put("result", result > 0);

        return response;

    }

}