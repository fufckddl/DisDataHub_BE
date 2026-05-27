package com.hub.gisdatahub.dataset.exception;

public class ValidationFailedException extends RuntimeException {
    
    private final Long uploadId;

    public ValidationFailedException(String message, Long uploadId) {
        super(message);
        this.uploadId = uploadId;
    }

    public Long getUploadId() {
        return uploadId;
    }
}   
