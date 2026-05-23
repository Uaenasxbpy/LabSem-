package com.labsem.dto.response;

import lombok.Data;

@Data
public class StudentStatsVO {
    private String studentName;
    private long reportCount;
    private long paperCount;
}
