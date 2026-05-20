package com.hub.gisdatahub.s3.type;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum S3PathType {
    UPLOAD_FILES("uploadFiles"),
    TEMP_FILES("tempFiles");

    private final String rootPath;

    S3PathType(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getRootPath() {
        return rootPath;
    }

    public static S3PathType fromFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filePath 값이 필요합니다.");
        }

        String normalizedFilePath = filePath.trim();
        for (S3PathType pathType : values()) {
            if (pathType.rootPath.equals(normalizedFilePath)) {
                return pathType;
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filePath 는 tempFiles 또는 uploadFiles 만 허용됩니다.");
    }
}
