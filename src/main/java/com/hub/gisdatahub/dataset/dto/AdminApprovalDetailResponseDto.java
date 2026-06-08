package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class AdminApprovalDetailResponseDto {
    private Integer datasetId;      // 데이터셋 고유 ID
    private String username;        // 요청자 이름 (sd_user.name)
    private String organization;    // 요청자 소속 (sd_user.organization)
    private String title;           // 데이터셋 제목
    private String fileFormat;      // 파일 포맷
    private Integer successCount;   // 총 성공 건수
    private String createdAt;       // 신청 일자 (문자열로 예쁘게 포맷팅해서 받을 예정)
}
