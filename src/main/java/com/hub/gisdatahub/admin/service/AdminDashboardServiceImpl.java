package com.hub.gisdatahub.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hub.gisdatahub.admin.dto.AdminDashboardSummaryDto;
import com.hub.gisdatahub.admin.mapper.AdminDashboardMapper;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    @Autowired
    private AdminDashboardMapper adminDashboardMapper;

    @Override
    public AdminDashboardSummaryDto getDashboardSummary() {

        AdminDashboardSummaryDto dashboardSummary =
            adminDashboardMapper.getDashboardSummary();

        return dashboardSummary;
    }

}