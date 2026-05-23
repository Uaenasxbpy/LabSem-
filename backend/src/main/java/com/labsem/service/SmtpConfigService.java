package com.labsem.service;

import com.labsem.entity.SmtpConfig;
import com.labsem.mapper.SmtpConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SmtpConfigService {

    private final SmtpConfigMapper smtpConfigMapper;

    /**
     * Get SMTP config (id=1), or return a default empty config if none exists.
     */
    public SmtpConfig get() {
        SmtpConfig config = smtpConfigMapper.selectById(1L);
        if (config == null) {
            config = new SmtpConfig();
            config.setId(1L);
            config.setHost("");
            config.setPort(465);
            config.setUsername("");
            config.setPassword("");
            config.setSenderName("");
            config.setUseTls(true);
        }
        return config;
    }

    /**
     * Update SMTP config. Creates with id=1 if not exists.
     * Only encodes password if non-empty.
     */
    public SmtpConfig update(String host, int port, String username, String password,
                             String senderName, Boolean useTls) {
        SmtpConfig config = smtpConfigMapper.selectById(1L);
        if (config == null) {
            config = new SmtpConfig();
            config.setId(1L);
            smtpConfigMapper.insert(config);
        }
        config.setHost(host != null ? host.trim() : "");
        config.setPort(port);
        config.setUsername(username != null ? username.trim() : "");
        if (password != null && !password.isEmpty()) {
            config.setPassword(Base64.getEncoder().encodeToString(password.getBytes()));
        }
        config.setSenderName(senderName != null ? senderName.trim() : "");
        config.setUseTls(useTls != null ? useTls : true);
        smtpConfigMapper.updateById(config);
        return config;
    }
}
