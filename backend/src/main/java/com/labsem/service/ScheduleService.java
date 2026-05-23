package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.exception.BusinessException;
import com.labsem.entity.Schedule;
import com.labsem.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;

    private static final Set<String> VALID_STATUSES = Set.of("upcoming", "completed", "cancelled");

    /**
     * List schedules with optional filters, ordered by meeting_date DESC.
     */
    public List<Schedule> list(String status, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Schedule::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(Schedule::getMeetingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(Schedule::getMeetingDate, endDate);
        }
        wrapper.orderByDesc(Schedule::getMeetingDate).orderByDesc(Schedule::getId);
        return scheduleMapper.selectList(wrapper);
    }

    /**
     * Create a new schedule.
     */
    public Schedule create(LocalDate meetingDate, String studentName, String topic,
                           String meetingFormat, String location, String notes) {
        Schedule schedule = new Schedule();
        schedule.setMeetingDate(meetingDate);
        schedule.setStudentName(studentName.trim());
        schedule.setTopic(topic != null && !topic.trim().isEmpty() ? topic.trim() : null);
        schedule.setMeetingFormat(meetingFormat);
        schedule.setLocation(location != null ? location.trim() : "");
        schedule.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        schedule.setStatus("upcoming");
        scheduleMapper.insert(schedule);
        return schedule;
    }

    /**
     * Update a schedule. Throws 404 if not found.
     */
    public Schedule update(Long scheduleId, LocalDate meetingDate, String studentName, String topic,
                           String meetingFormat, String location, String notes) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException.notFound("排期不存在");
        }
        schedule.setMeetingDate(meetingDate);
        schedule.setStudentName(studentName.trim());
        schedule.setTopic(topic != null && !topic.trim().isEmpty() ? topic.trim() : null);
        schedule.setMeetingFormat(meetingFormat);
        schedule.setLocation(location != null ? location.trim() : "");
        schedule.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        scheduleMapper.updateById(schedule);
        return schedule;
    }

    /**
     * Delete a schedule. Throws 404 if not found.
     */
    public void delete(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException.notFound("排期不存在");
        }
        scheduleMapper.deleteById(scheduleId);
    }

    /**
     * Update schedule status. Validates status value.
     */
    public void updateStatus(Long scheduleId, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(400, "无效状态");
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException.notFound("排期不存在");
        }
        schedule.setStatus(status);
        scheduleMapper.updateById(schedule);
    }
}
