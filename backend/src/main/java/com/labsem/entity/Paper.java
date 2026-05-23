package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("papers")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("report_id")
    private Long reportId;

    @TableField("title_raw")
    private String titleRaw;

    @TableField("title_normalized")
    private String titleNormalized;

    @TableField("duplicate_status")
    private String duplicateStatus;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
