package com.hub.gisdatahub.dataset.dto;

import lombok.Data;

@Data
public class ValidationErrorDto {
    private Long errorId;
    private Long uploadId;
    private Integer rowNumber;
    private String columnName;
    private String errorType;
    private String errorMessage;
    private String rawValue;
}
