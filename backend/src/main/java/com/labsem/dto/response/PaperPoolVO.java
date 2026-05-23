package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaperPoolVO {
    private Long id;
    private String title;
    private String url;
    private String recommendedBy;
    private String claimedBy;
    private String status;
    private Long reportId;
    private String notes;
    private LocalDateTime createdAt;
}
