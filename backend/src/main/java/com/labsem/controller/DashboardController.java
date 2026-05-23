package com.labsem.controller;

import com.labsem.dto.response.DashboardStatsVO;
import com.labsem.dto.response.MonthlyStatsVO;
import com.labsem.dto.response.StudentStatsVO;
import com.labsem.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/stats
     * Returns overall dashboard statistics.
     */
    @GetMapping("/api/dashboard/stats")
    public DashboardStatsVO stats() {
        return dashboardService.getStats();
    }

    /**
     * GET /api/dashboard/by-student
     * Returns per-student report and paper counts.
     */
    @GetMapping("/api/dashboard/by-student")
    public List<StudentStatsVO> byStudent() {
        return dashboardService.getByStudent();
    }

    /**
     * GET /api/dashboard/monthly
     * Returns monthly report and paper counts.
     */
    @GetMapping("/api/dashboard/monthly")
    public List<MonthlyStatsVO> monthly() {
        return dashboardService.getMonthly();
    }
}
