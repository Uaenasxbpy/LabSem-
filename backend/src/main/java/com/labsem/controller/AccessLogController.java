package com.labsem.controller;

import com.labsem.entity.AccessLog;
import com.labsem.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    /**
     * GET /api/logs
     * Returns a raw array of access log entries.
     * Optional query params: ip, keyword, start_date, end_date, limit
     */
    @GetMapping("/api/logs")
    public List<AccessLog> list(
            @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return accessLogService.list(ip, keyword, startDate, endDate, limit != null ? limit : 200);
    }
}
