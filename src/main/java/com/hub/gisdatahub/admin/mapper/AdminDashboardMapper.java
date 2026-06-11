package com.hub.gisdatahub.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.admin.dto.AdminDashboardSummaryDto;

@Mapper
public interface AdminDashboardMapper {

    public AdminDashboardSummaryDto getDashboardSummary();

}