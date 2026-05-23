package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.dto.response.DashboardStatsVO;
import com.labsem.dto.response.MonthlyStatsVO;
import com.labsem.dto.response.StudentStatsVO;
import com.labsem.entity.Member;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.mapper.DashboardMapper;
import com.labsem.mapper.MemberMapper;
import com.labsem.mapper.PaperMapper;
import com.labsem.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    private final ReportMapper reportMapper;
    private final PaperMapper paperMapper;
    private final MemberMapper memberMapper;

    /**
     * Get dashboard statistics: totalReports, totalPapers, totalMembers, monthlyReports.
     */
    public DashboardStatsVO getStats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setTotalReports(reportMapper.selectCount(null));
        vo.setTotalPapers(paperMapper.selectCount(null));
        vo.setTotalMembers(memberMapper.selectCount(null));

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Long monthlyReports = reportMapper.selectCount(
                new LambdaQueryWrapper<Report>().ge(Report::getReportDate, monthStart)
        );
        vo.setMonthlyReports(monthlyReports);

        return vo;
    }

    /**
     * Get statistics grouped by student.
     */
    public List<StudentStatsVO> getByStudent() {
        List<Map<String, Object>> reportCounts = dashboardMapper.selectStudentReportCount();
        List<Map<String, Object>> paperCounts = dashboardMapper.selectStudentPaperCount();

        // Merge by student_name
        Map<String, StudentStatsVO> map = new LinkedHashMap<>();
        for (Map<String, Object> row : reportCounts) {
            String name = (String) row.get("student_name");
            StudentStatsVO vo = new StudentStatsVO();
            vo.setStudentName(name);
            vo.setReportCount(((Number) row.get("report_count")).longValue());
            map.put(name, vo);
        }
        for (Map<String, Object> row : paperCounts) {
            String name = (String) row.get("student_name");
            StudentStatsVO vo = map.getOrDefault(name, new StudentStatsVO());
            vo.setStudentName(name);
            vo.setPaperCount(((Number) row.get("paper_count")).longValue());
            map.putIfAbsent(name, vo);
        }

        // Sort by reportCount DESC
        List<StudentStatsVO> result = new ArrayList<>(map.values());
        result.sort((a, b) -> Long.compare(b.getReportCount(), a.getReportCount()));
        return result;
    }

    /**
     * Get monthly statistics for the last 12 months.
     */
    public List<MonthlyStatsVO> getMonthly() {
        List<Map<String, Object>> reportCounts = dashboardMapper.selectMonthlyReportCount();
        List<Map<String, Object>> paperCounts = dashboardMapper.selectMonthlyPaperCount();

        // Merge by month
        Map<String, MonthlyStatsVO> map = new LinkedHashMap<>();
        for (Map<String, Object> row : reportCounts) {
            String month = (String) row.get("month");
            MonthlyStatsVO vo = new MonthlyStatsVO();
            vo.setMonth(month);
            vo.setReportCount(((Number) row.get("report_count")).longValue());
            map.put(month, vo);
        }
        for (Map<String, Object> row : paperCounts) {
            String month = (String) row.get("month");
            MonthlyStatsVO vo = map.getOrDefault(month, new MonthlyStatsVO());
            vo.setMonth(month);
            vo.setPaperCount(((Number) row.get("paper_count")).longValue());
            map.putIfAbsent(month, vo);
        }

        // Return last 12 months
        List<MonthlyStatsVO> all = new ArrayList<>(map.values());
        if (all.size() > 12) {
            return all.subList(all.size() - 12, all.size());
        }
        return all;
    }
}
