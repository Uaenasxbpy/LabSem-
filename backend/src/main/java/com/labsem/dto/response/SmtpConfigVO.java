package com.labsem.dto.response;

import lombok.Data;

@Data
public class SmtpConfigVO {
    private Long id;
    private String host;
    private Integer port;
    private String username;
    private String passwordMasked;
    private String senderName;
    private Boolean useTls;
}
