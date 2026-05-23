package com.labsem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("smtp_config")
public class SmtpConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("host")
    private String host;

    @TableField("port")
    private Integer port;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("sender_name")
    private String senderName;

    @TableField("use_tls")
    private Boolean useTls;
}
