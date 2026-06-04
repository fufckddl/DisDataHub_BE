package com.hub.gisdatahub.admin.service;

import java.util.List;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;

public interface AdminUserService {

    List<UserManagementTypeDto> getUserManagementTypeList();

    Integer registerUserManagementLog(UserManagementLogDto userManagementLogDto);
}
