package com.labsem.controller;

import com.labsem.common.exception.BusinessException;
import com.labsem.dto.request.ReportSearchParams;
import com.labsem.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * POST /api/reports
     * Create a new report with uploaded papers and optional PPT.
     * Returns the result map directly: {ok, need_confirm, warning, report_id, folder_name, duplicates}
     */
    @PostMapping("/api/reports")
    public Map<String, Object> createReport(
            @RequestParam("student_name") String studentName,
            @RequestParam("report_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam("paper_titles") List<String> paperTitles,
            @RequestParam("paper_pdfs") List<MultipartFile> paperPdfs,
            @RequestParam(value = "ppt_file", required = false) MultipartFile pptFile,
            @RequestParam(value = "confirm_duplicate", defaultValue = "false") boolean confirmDuplicate
    ) {
        // Convert MultipartFiles to bytes and extract names
        List<byte[]> paperPdfBytes = new ArrayList<>();
        List<String> paperPdfNames = new ArrayList<>();
        for (MultipartFile f : paperPdfs) {
            try {
                paperPdfBytes.add(f.getBytes());
                paperPdfNames.add(f.getOriginalFilename());
            } catch (IOException e) {
                throw new BusinessException(400, "文件读取失败: " + e.getMessage());
            }
        }

        byte[] pptBytes = null;
        String pptName = null;
        if (pptFile != null && !pptFile.isEmpty()) {
            try {
                pptBytes = pptFile.getBytes();
                pptName = pptFile.getOriginalFilename();
            } catch (IOException e) {
                throw new BusinessException(400, "PPT文件读取失败: " + e.getMessage());
            }
        }

        return reportService.createReport(studentName, reportDate, paperTitles,
                paperPdfBytes, paperPdfNames, pptBytes, pptName, confirmDuplicate);
    }

    /**
     * GET /api/reports
     * Search reports with optional filters.
     * Returns a raw array (List) of report objects with nested papers and files.
     * All keys are snake_case for frontend compatibility.
     */
    @GetMapping("/api/reports")
    public List<Map<String, Object>> searchReports(ReportSearchParams params) {
        return reportService.search(
                params.getKeyword(),
                params.getStudentName(),
                params.getStartDate(),
                params.getEndDate(),
                params.getLimit() != null ? params.getLimit() : 100
        );
    }

    /**
     * DELETE /api/reports/{reportId}
     * Delete a report and all associated files.
     * Returns {"ok": true, "message": "已删除整条汇报记录"}
     */
    @DeleteMapping("/api/reports/{reportId}")
    public Map<String, Object> deleteReport(@PathVariable Long reportId) {
        reportService.deleteReport(reportId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已删除整条汇报记录");
        return result;
    }
}
