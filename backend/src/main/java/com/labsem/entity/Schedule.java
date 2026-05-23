package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("schedules")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("meeting_date")
    private LocalDate meetingDate;

    @TableField("student_name")
    private String studentName;

    @TableField("topic")
    private String topic;

    @TableField("meeting_format")
    private String meetingFormat;

    @TableField("location")
    private String location;

    @TableField("status")
    private String status;

    @TableField("notes")
    private String notes;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
