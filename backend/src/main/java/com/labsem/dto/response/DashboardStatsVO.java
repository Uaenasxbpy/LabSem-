package com.labsem.dto.response;

import lombok.Data;

@Data
public class DashboardStatsVO {
    private long totalReports;
    private long totalPapers;
    private long totalMembers;
    private Long monthlyReports;
}
