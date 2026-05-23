package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("access_logs")
public class AccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("action")
    private String action;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("report_id")
    private Long reportId;

    @TableField("report_student_name")
    private String reportStudentName;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("paper_title")
    private String paperTitle;

    @TableField("accessed_at")
    private LocalDateTime accessedAt;
}
