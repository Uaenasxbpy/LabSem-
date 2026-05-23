package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_files")
public class LabFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("tags")
    private String tags;

    @TableField("original_name")
    private String originalName;

    @JsonIgnore
    @TableField("storage_name")
    private String storageName;

    @JsonIgnore
    @TableField("storage_path")
    private String storagePath;

    @TableField("file_size")
    private Long fileSize;

    @JsonIgnore
    @TableField("file_hash")
    private String fileHash;

    @TableField("uploaded_by")
    private String uploadedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
