package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AccessLogVO {
    private Long id;
    private String ipAddress;
    private String action;
    private String fileName;
    private String fileType;
    private Long reportId;
    private String reportStudentName;
    private LocalDate reportDate;
    private String paperTitle;
    private LocalDateTime accessedAt;
}
