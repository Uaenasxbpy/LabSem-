package com.labsem.dto.response;

import lombok.Data;

@Data
public class PaperVO {
    private Long id;
    private String titleRaw;
    private String duplicateStatus;
}
