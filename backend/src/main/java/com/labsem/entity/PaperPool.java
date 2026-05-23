package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_pool")
public class PaperPool {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("url")
    private String url;

    @TableField("recommended_by")
    private String recommendedBy;

    @TableField("claimed_by")
    private String claimedBy;

    @TableField("status")
    private String status;

    @TableField("report_id")
    private Long reportId;

    @TableField("notes")
    private String notes;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
