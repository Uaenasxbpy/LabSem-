package com.labsem.controller;

import com.labsem.dto.response.SmtpConfigVO;
import com.labsem.entity.SmtpConfig;
import com.labsem.service.SmtpConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SmtpConfigController {

    private final SmtpConfigService smtpConfigService;

    /**
     * GET /api/smtp-config
     * Returns the SMTP configuration with password masked as "******".
     */
    @GetMapping("/api/smtp-config")
    public SmtpConfigVO get() {
        SmtpConfig config = smtpConfigService.get();
        SmtpConfigVO vo = new SmtpConfigVO();
        vo.setId(config.getId());
        vo.setHost(config.getHost());
        vo.setPort(config.getPort());
        vo.setUsername(config.getUsername());
        vo.setPasswordMasked("******");
        vo.setSenderName(config.getSenderName());
        vo.setUseTls(config.getUseTls());
        return vo;
    }

    /**
     * PUT /api/smtp-config
     * Update SMTP configuration. Form params.
     */
    @PutMapping("/api/smtp-config")
    public SmtpConfigVO update(
            @RequestParam(value = "host", required = false) String host,
            @RequestParam(value = "port", required = false) Integer port,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "sender_name", required = false) String senderName,
            @RequestParam(value = "use_tls", required = false) Boolean useTls
    ) {
        SmtpConfig config = smtpConfigService.update(
                host, port != null ? port : 465, username, password, senderName, useTls);
        SmtpConfigVO vo = new SmtpConfigVO();
        vo.setId(config.getId());
        vo.setHost(config.getHost());
        vo.setPort(config.getPort());
        vo.setUsername(config.getUsername());
        vo.setPasswordMasked("******");
        vo.setSenderName(config.getSenderName());
        vo.setUseTls(config.getUseTls());
        return vo;
    }
}
