package com.hub.gisdatahub.download.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 백엔드가 변환해서 만든 파일 자체를 Controller에 넘길 때 사용 Dto
@Data
@AllArgsConstructor
public class DownloadExportResultDto {
    private String fileName; // 실제 저장될 파일명
    private String contentType; // 응답 content-Type;
    private byte[] bytes; // 다운로드할 파일 바이트
}
