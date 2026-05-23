package com.labsem.controller;

import com.labsem.entity.Schedule;
import com.labsem.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * GET /api/schedules
     * Returns {"schedules": [...]}
     * Optional query params: month (yyyy-MM), status
     */
    @GetMapping("/api/schedules")
    public Map<String, Object> listSchedules(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "status", required = false) String status
    ) {
        LocalDate startDate = null;
        LocalDate endDate = null;
        if (month != null && !month.isEmpty()) {
            java.time.YearMonth ym = java.time.YearMonth.parse(month);
            startDate = ym.atDay(1);
            endDate = ym.atEndOfMonth();
        }
        List<Schedule> schedules = scheduleService.list(status, startDate, endDate);
        return Map.of("schedules", schedules);
    }

    /**
     * POST /api/schedules
     * Create a new schedule. Form params (multipart/form-data).
     */
    @PostMapping("/api/schedules")
    public Schedule createSchedule(
            @RequestParam("meeting_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate meetingDate,
            @RequestParam("student_name") String studentName,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "meeting_format", required = false) String meetingFormat,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        return scheduleService.create(meetingDate, studentName, topic, meetingFormat, location, notes);
    }

    /**
     * PUT /api/schedules/{id}
     * Update an existing schedule.
     */
    @PutMapping("/api/schedules/{id}")
    public Schedule updateSchedule(
            @PathVariable Long id,
            @RequestParam(value = "meeting_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate meetingDate,
            @RequestParam(value = "student_name", required = false) String studentName,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "meeting_format", required = false) String meetingFormat,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        return scheduleService.update(id, meetingDate, studentName, topic, meetingFormat, location, notes);
    }

    /**
     * DELETE /api/schedules/{id}
     * Returns {"ok": true, "message": "已删除日程"}
     */
    @DeleteMapping("/api/schedules/{id}")
    public Map<String, Object> deleteSchedule(@PathVariable Long id) {
        scheduleService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已删除日程");
        return result;
    }

    /**
     * PUT /api/schedules/{id}/status
     * Update schedule status only.
     * Returns {"ok": true, "status": "new_status"}
     */
    @PutMapping("/api/schedules/{id}/status")
    public Map<String, Object> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") String status
    ) {
        scheduleService.updateStatus(id, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("status", status);
        return result;
    }
}
