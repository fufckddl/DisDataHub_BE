package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class MyUploadResponseDto {
    private Long datasetId;
    private String title;
    private String category;
    private String fileFormat;
    private String status;       // REQUEST, APPROVED, REJECTED
    private String createdAt;    // 업로드 일시 (YYYY-MM-DD HH24:MI 포맷)
    
    // 반려된 건일 경우에만 이 객체에 데이터가 채워집니다. 승인/대기 중일 때는 null로 내려갑니다.
    private RejectionDetailDto rejectionDetails;
}
