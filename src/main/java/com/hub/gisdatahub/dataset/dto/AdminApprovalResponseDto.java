package com.hub.gisdatahub.dataset.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminApprovalResponseDto {
    private Long datasetId;          // 데이터셋 고유 PK
    private String username;         // 연구자 진짜 이름 (sd_user)
    private String organization;     // 연구자 소속 기관 (sd_user)
    private String title;            // 데이터셋 제목 (sd_gis_dataset)
    private Integer successCount;    // 최종 파싱 성공 건수 (sd_upload_log)
    private LocalDateTime createdAt; // 신청 일자 (sd_gis_dataset)
    private String status;           // 현재 상태 (REQUEST 고정)
}
