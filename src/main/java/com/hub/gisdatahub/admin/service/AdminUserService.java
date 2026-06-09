package com.hub.gisdatahub.admin.service;

import java.util.List;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;
import com.hub.gisdatahub.user.domain.User;

public interface AdminUserService {
    // 사용자 목록
    public List<User> getUserList();

    // 사용자 디테일
    public User getUser(Integer id);

    // 사용자 제제
    public List<UserManagementTypeDto> getManagementTypeList();
    public void applyUserManagement(UserManagementLogDto userManagementLogDto, String status);
}
