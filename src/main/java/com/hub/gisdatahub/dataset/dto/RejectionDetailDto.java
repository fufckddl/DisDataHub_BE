package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class RejectionDetailDto {
    private String adminName;    // 반려한 관리자 이름 (sd_user의 name)
    private String rejectReason; // 반려 사유
    private String rejectedAt;   // 반려 일시 (YYYY-MM-DD HH24:MI 포맷)
}
