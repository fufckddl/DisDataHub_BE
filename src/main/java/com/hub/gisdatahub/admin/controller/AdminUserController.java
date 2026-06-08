package com.hub.gisdatahub.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;
import com.hub.gisdatahub.admin.service.AdminUserService;
import com.hub.gisdatahub.user.domain.User;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @GetMapping("findUserList")
    public Map<String, Object> findUserList() {
        Map<String, Object> response = new HashMap<>();

        List<User> userList = adminUserService.getUserList();

        response.put("userList", userList);
        response.put("result", "success");

        return response;
    }

    @GetMapping("userDetail/{id}")
    public Map<String, Object> userDetail(@PathVariable("id") Integer id) {
        Map<String, Object> response = new HashMap<>();

        User user = adminUserService.getUser(id);

        response.put("user", user);
        response.put("result", "success");

        return response;
    }

    @GetMapping("findUserManagementTypeList")
    public Map<String, Object> findUserManagementTypeList() {

        Map<String, Object> response = new HashMap<>();

        List<UserManagementTypeDto> userManagementTypeList = adminUserService.getManagementTypeList();

        response.put("userManagementTypeList", userManagementTypeList);
        response.put("result", "success");

        return response;
    }

    @PostMapping("applyUserManagement")
    public Map<String, Object> applyUserManagement(
        @RequestBody UserManagementLogDto userManagementLogDto,
        @RequestParam("status") String status
    ) {
        Map<String, Object> response = new HashMap<>();

        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

        Integer adminUserId =
            Integer.parseInt(authentication.getName());

        userManagementLogDto.setAdminUserId(adminUserId);

        adminUserService.applyUserManagement(userManagementLogDto, status);

        response.put("result", "success");

        return response;
    }
}