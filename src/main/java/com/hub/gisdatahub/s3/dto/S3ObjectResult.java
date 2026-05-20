package com.hub.gisdatahub.s3.dto;

public record S3ObjectResult(
        String bucket,
        String filePath,
        String storedFilename,
        String originalFilename,
        String contentType,
        String eTag,
        Long size,
        String action
) {
}
