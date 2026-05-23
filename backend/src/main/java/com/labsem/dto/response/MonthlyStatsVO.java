package com.labsem.dto.response;

import lombok.Data;

@Data
public class MonthlyStatsVO {
    private String month;
    private long reportCount;
    private long paperCount;
}
