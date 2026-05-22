package com.hub.gisdatahub.board.dto;

import lombok.Data;

@Data
public class CommonCodeDto {
    private String codeGroup;
    private String code;
    private String codeName;
    private Integer sortOrder;
    private String useYn;
}
