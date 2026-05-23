package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.entity.AccessLog;
import com.labsem.entity.StoredFile;
import com.labsem.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogMapper accessLogMapper;

    /**
     * List access logs with optional filters, ordered by accessed_at DESC.
     */
    public List<AccessLog> list(String ip, String keyword, LocalDate startDate, LocalDate endDate, int limit) {
        LambdaQueryWrapper<AccessLog> wrapper = new LambdaQueryWrapper<>();

        if (ip != null && !ip.trim().isEmpty()) {
            wrapper.like(AccessLog::getIpAddress, ip.trim());
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(AccessLog::getFileName, kw)
                    .or().like(AccessLog::getPaperTitle, kw)
                    .or().like(AccessLog::getReportStudentName, kw)
            );
        }

        if (startDate != null) {
            wrapper.ge(AccessLog::getReportDate, startDate);
        }

        if (endDate != null) {
            wrapper.le(AccessLog::getReportDate, endDate);
        }

        wrapper.orderByDesc(AccessLog::getAccessedAt).orderByDesc(AccessLog::getId);
        wrapper.last("LIMIT " + limit);
        return accessLogMapper.selectList(wrapper);
    }

    /**
     * Log an access event for a file record.
     */
    public void log(String ipAddress, String action, StoredFile fileRecord) {
        AccessLog log = new AccessLog();
        log.setIpAddress(ipAddress);
        log.setAction(action);
        log.setFileName(fileRecord.getOriginalName());
        log.setFileType(fileRecord.getFileType());
        log.setReportId(fileRecord.getReportId());
        accessLogMapper.insert(log);
    }
}
