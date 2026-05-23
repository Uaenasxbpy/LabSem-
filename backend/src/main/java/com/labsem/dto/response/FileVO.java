package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVO {
    private Long id;
    private String fileType;
    private String originalName;
    private Long paperId;
    private LocalDateTime createdAt;
}
