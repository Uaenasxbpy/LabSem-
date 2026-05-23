package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("files")
public class StoredFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("report_id")
    private Long reportId;

    @TableField("paper_id")
    private Long paperId;

    @TableField("file_type")
    private String fileType;

    @TableField("original_name")
    private String originalName;

    @JsonIgnore
    @TableField("storage_name")
    private String storageName;

    @JsonIgnore
    @TableField("storage_path")
    private String storagePath;

    @JsonIgnore
    @TableField("file_hash")
    private String fileHash;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
