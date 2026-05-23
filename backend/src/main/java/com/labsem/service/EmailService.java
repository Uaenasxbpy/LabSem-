package com.labsem.service;

import com.labsem.constant.AppConstants;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.entity.StoredFile;
import com.labsem.mapper.SmtpConfigMapper;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final SmtpConfigMapper smtpConfigMapper;

    /**
     * Send report notification email to recipients.
     */
    public Map<String, Object> sendReportNotification(Report report, List<Paper> papers,
                                                       List<StoredFile> files, List<String> recipients) {
        com.labsem.entity.SmtpConfig config = smtpConfigMapper.selectById(1L);
        if (config == null || config.getHost() == null || config.getHost().isEmpty()) {
            return Map.of("ok", false, "error", "未配置SMTP信息，请先在邮件设置中配置");
        }
        if (recipients == null || recipients.isEmpty()) {
            return Map.of("ok", false, "error", "收件人列表为空，请先添加实验室成员");
        }

        String subject = "文献汇报通知 - " + report.getStudentName() + " - " + report.getReportDate();

        StringBuilder paperLines = new StringBuilder();
        for (int i = 0; i < papers.size(); i++) {
            paperLines.append("  ").append(i + 1).append(". ").append(papers.get(i).getTitleRaw()).append("\n");
        }
        String body = "各位好，\n\n"
                + report.getStudentName() + " 同学于 " + report.getReportDate() + " 进行了文献汇报，"
                + "汇报论文如下：\n\n" + paperLines + "\n"
                + "请查收附件中的材料。\n\n"
                + "—— 实验室文献管理系统";

        List<String> tempDirs = new ArrayList<>();
        try {
            List<Map.Entry<String, String>> attachments = compressFilesByReport(files);
            collectTempDirs(attachments, tempDirs);
            List<List<Map.Entry<String, String>>> batches = splitAttachments(attachments);

            for (int i = 0; i < batches.size(); i++) {
                String batchSubject = batches.size() == 1 ? subject
                        : subject + "（" + (i + 1) + "/" + batches.size() + "）";
                Map<String, Object> result = sendSmtpEmail(config, recipients, batchSubject, body, batches.get(i));
                if (!(Boolean) result.get("ok")) {
                    return result;
                }
            }
            return Map.of("ok", true, "message",
                    "已成功发送给 " + recipients.size() + " 位成员（共 " + batches.size() + " 封邮件）");
        } catch (Exception e) {
            return Map.of("ok", false, "error", "邮件发送失败: " + e.getMessage());
        } finally {
            cleanupTempDirs(tempDirs);
        }
    }

    /**
     * Send custom email to recipients.
     */
    public Map<String, Object> sendCustomEmail(List<String> recipients, String subject, String body,
                                                List<StoredFile> files) {
        com.labsem.entity.SmtpConfig config = smtpConfigMapper.selectById(1L);
        if (config == null || config.getHost() == null || config.getHost().isEmpty()) {
            return Map.of("ok", false, "error", "未配置SMTP信息，请先在邮件设置中配置");
        }
        if (recipients == null || recipients.isEmpty()) {
            return Map.of("ok", false, "error", "收件人列表为空");
        }

        List<String> tempDirs = new ArrayList<>();
        try {
            List<Map.Entry<String, String>> attachments = compressFilesByReport(files);
            collectTempDirs(attachments, tempDirs);
            List<List<Map.Entry<String, String>>> batches = splitAttachments(attachments);

            for (int i = 0; i < batches.size(); i++) {
                String batchSubject = batches.size() == 1 ? subject
                        : subject + "（" + (i + 1) + "/" + batches.size() + "）";
                Map<String, Object> result = sendSmtpEmail(config, recipients, batchSubject, body, batches.get(i));
                if (!(Boolean) result.get("ok")) {
                    return result;
                }
            }
            return Map.of("ok", true, "message",
                    "已成功发送给 " + recipients.size() + " 位成员（共 " + batches.size() + " 封邮件）");
        } catch (Exception e) {
            return Map.of("ok", false, "error", "邮件发送失败: " + e.getMessage());
        } finally {
            cleanupTempDirs(tempDirs);
        }
    }

    /**
     * Collect temp directory paths from attachment entries for cleanup.
     */
    private void collectTempDirs(List<Map.Entry<String, String>> attachments, List<String> tempDirs) {
        for (Map.Entry<String, String> entry : attachments) {
            String path = entry.getKey();
            if (path.contains("labsem_mail_")) {
                String dir = Path.of(path).getParent().toString();
                if (!tempDirs.contains(dir)) {
                    tempDirs.add(dir);
                }
            }
        }
    }

    /**
     * Send email via SMTP using jakarta.mail API.
     * Supports SSL (port 465) and STARTTLS (port 587).
     *
     * @param attachmentPaths list of (storagePath, originalName) pairs
     */
    public Map<String, Object> sendSmtpEmail(com.labsem.entity.SmtpConfig config, List<String> recipients,
                                              String subject, String body,
                                              List<Map.Entry<String, String>> attachmentPaths) {
        String password = decodePassword(config.getPassword());

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.connectiontimeout", "30000");

        if (Boolean.TRUE.equals(config.getUseTls())) {
            // SSL on port 465
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.port", String.valueOf(config.getPort()));
            props.put("mail.smtp.host", config.getHost());
        } else {
            // STARTTLS on port 587
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.port", String.valueOf(config.getPort()));
            props.put("mail.smtp.host", config.getHost());
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getUsername(), password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(config.getSenderName() != null && !config.getSenderName().isEmpty()
                    ? new InternetAddress(config.getUsername(), config.getSenderName(), "UTF-8")
                    : new InternetAddress(config.getUsername()));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(String.join(",", recipients)));
            message.setSubject(subject, "UTF-8");

            // Body
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(body, "UTF-8", "plain");

            jakarta.mail.Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);

            // Attachments
            if (attachmentPaths != null) {
                for (Map.Entry<String, String> entry : attachmentPaths) {
                    String storagePath = entry.getKey();
                    String originalName = entry.getValue();
                    File file = new File(storagePath);
                    if (!file.exists()) continue;

                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(file);
                    attachmentPart.setDataHandler(new DataHandler(source));
                    attachmentPart.setFileName(MimeUtility.encodeText(originalName, "UTF-8", "B"));
                    multipart.addBodyPart(attachmentPart);
                }
            }

            message.setContent(multipart);

            Transport.send(message);
            return Map.of("ok", true, "message", "已成功发送给 " + recipients.size() + " 位成员");
        } catch (Exception e) {
            return Map.of("ok", false, "error", "邮件发送失败: " + e.getMessage());
        }
    }

    // ---- Helper methods ----

    /**
     * Compress files by report. If total size > 45MB, zip by report_id.
     * Returns list of (storagePath, originalName) pairs.
     */
    public List<Map.Entry<String, String>> compressFilesByReport(List<StoredFile> files) throws IOException {
        long totalSize = 0;
        for (StoredFile f : files) {
            File file = new File(f.getStoragePath());
            if (file.exists()) {
                totalSize += file.length();
            }
        }

        if (totalSize <= AppConstants.MAX_EMAIL_ATTACHMENT_SIZE) {
            List<Map.Entry<String, String>> result = new ArrayList<>();
            for (StoredFile f : files) {
                if (new File(f.getStoragePath()).exists()) {
                    result.add(new AbstractMap.SimpleEntry<>(f.getStoragePath(), f.getOriginalName()));
                }
            }
            return result;
        }

        // Group by report_id and zip each group
        Map<Long, List<StoredFile>> groups = files.stream()
                .collect(Collectors.groupingBy(StoredFile::getReportId));

        Path tempDir = Files.createTempDirectory("labsem_mail_");
        List<Map.Entry<String, String>> attachments = new ArrayList<>();

        for (Map.Entry<Long, List<StoredFile>> entry : groups.entrySet()) {
            Long reportId = entry.getKey();
            List<StoredFile> groupFiles = entry.getValue();
            String zipName = "report_" + reportId + ".zip";
            Path zipPath = tempDir.resolve(zipName);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                for (StoredFile f : groupFiles) {
                    File file = new File(f.getStoragePath());
                    if (file.exists()) {
                        zos.putNextEntry(new ZipEntry(f.getOriginalName()));
                        zos.write(Files.readAllBytes(file.toPath()));
                        zos.closeEntry();
                    }
                }
            }

            if (Files.size(zipPath) > AppConstants.MAX_EMAIL_ATTACHMENT_SIZE) {
                deleteTempDir(tempDir);
                throw new IOException("以下汇报的文件即使压缩后仍超出 45MB 限制，无法发送："
                        + zipName + "（" + Math.round(Files.size(zipPath) / 1024.0 / 1024.0 * 10) / 10.0 + "MB）。"
                        + "请检查是否有不必要的大文件。");
            }
            attachments.add(new AbstractMap.SimpleEntry<>(zipPath.toString(), zipName));
        }

        return attachments;
    }

    /**
     * Split attachments into batches respecting the 45MB limit.
     */
    public List<List<Map.Entry<String, String>>> splitAttachments(List<Map.Entry<String, String>> attachments) {
        List<List<Map.Entry<String, String>>> batches = new ArrayList<>();
        List<Map.Entry<String, String>> currentBatch = new ArrayList<>();
        long currentSize = 0;

        for (Map.Entry<String, String> entry : attachments) {
            File file = new File(entry.getKey());
            long fsize = file.exists() ? file.length() : 0;
            if (currentSize + fsize > AppConstants.MAX_EMAIL_ATTACHMENT_SIZE && !currentBatch.isEmpty()) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentSize = 0;
            }
            currentBatch.add(entry);
            currentSize += fsize;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        return batches;
    }

    /**
     * Cleanup temporary directories.
     */
    public void cleanupTempDirs(List<String> tempDirs) {
        for (String dir : tempDirs) {
            deleteTempDir(Path.of(dir));
        }
    }

    private void deleteTempDir(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path child : stream.toList()) {
                        Files.deleteIfExists(child);
                    }
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
        }
    }

    private String decodePassword(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            return encoded;
        }
    }
}
