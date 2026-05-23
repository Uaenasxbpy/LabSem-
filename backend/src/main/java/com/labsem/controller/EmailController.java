package com.labsem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.dto.request.EmailSendRequest;
import com.labsem.entity.Member;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.entity.StoredFile;
import com.labsem.mapper.MemberMapper;
import com.labsem.mapper.PaperMapper;
import com.labsem.mapper.ReportMapper;
import com.labsem.mapper.StoredFileMapper;
import com.labsem.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final ReportMapper reportMapper;
    private final PaperMapper paperMapper;
    private final StoredFileMapper storedFileMapper;
    private final MemberMapper memberMapper;

    /**
     * POST /api/reports/{reportId}/notify
     * Send email notification for a specific report to all lab members.
     * Returns the result map from EmailService directly.
     */
    @PostMapping("/api/reports/{reportId}/notify")
    public Map<String, Object> notifyReport(@PathVariable Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            return Map.of("ok", false, "error", "汇报记录不存在");
        }

        List<Paper> papers = paperMapper.selectList(
                new LambdaQueryWrapper<Paper>().eq(Paper::getReportId, reportId));
        List<StoredFile> files = storedFileMapper.selectList(
                new LambdaQueryWrapper<StoredFile>().eq(StoredFile::getReportId, reportId));
        List<String> recipients = memberMapper.selectList(null).stream()
                .map(Member::getEmail)
                .collect(Collectors.toList());

        return emailService.sendReportNotification(report, papers, files, recipients);
    }

    /**
     * POST /api/emails/send
     * Send a custom email with optional file attachments.
     * Accepts JSON body: {member_ids, file_ids, subject, body}
     * Returns the result map from EmailService directly.
     */
    @PostMapping("/api/emails/send")
    public Map<String, Object> sendEmail(@RequestBody EmailSendRequest request) {
        // Load recipients from member IDs
        List<String> recipients;
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            List<Member> members = memberMapper.selectBatchIds(request.getMemberIds());
            recipients = members.stream().map(Member::getEmail).collect(Collectors.toList());
        } else {
            recipients = memberMapper.selectList(null).stream()
                    .map(Member::getEmail).collect(Collectors.toList());
        }

        // Load files from file IDs
        List<StoredFile> files = List.of();
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            files = storedFileMapper.selectBatchIds(request.getFileIds());
        }

        return emailService.sendCustomEmail(recipients, request.getSubject(), request.getBody(), files);
    }
}
