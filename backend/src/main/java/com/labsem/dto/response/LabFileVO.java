package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LabFileVO {
    private Long id;
    private String title;
    private String description;
    private String tags;
    private String originalName;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime createdAt;
}
