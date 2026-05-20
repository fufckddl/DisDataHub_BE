package com.hub.gisdatahub.s3.dto;

import org.springframework.core.io.ByteArrayResource;

public record S3DownloadResult(
        String bucket,
        String key,
        String fileName,
        String contentType,
        ByteArrayResource resource
) {
}
