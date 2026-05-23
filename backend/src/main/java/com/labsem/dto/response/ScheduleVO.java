package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduleVO {
    private Long id;
    private LocalDate meetingDate;
    private String studentName;
    private String topic;
    private String meetingFormat;
    private String location;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
}
