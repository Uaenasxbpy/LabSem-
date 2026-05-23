-- LabSem 数据库建表脚本 (MySQL 8.0+)
-- 字符集: utf8mb4, 排序规则: utf8mb4_unicode_ci

CREATE DATABASE IF NOT EXISTS labsem
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE labsem;

-- 学生表
CREATE TABLE IF NOT EXISTS students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_students_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 汇报表
CREATE TABLE IF NOT EXISTS reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    student_name VARCHAR(128) NOT NULL,
    report_date DATE NOT NULL,
    folder_name VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reports_folder_name (folder_name),
    KEY idx_reports_student_name (student_name),
    KEY idx_reports_report_date (report_date),
    CONSTRAINT fk_reports_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 论文表
CREATE TABLE IF NOT EXISTS papers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    title_raw TEXT NOT NULL,
    title_normalized VARCHAR(512) NOT NULL DEFAULT '',
    duplicate_status VARCHAR(32) NOT NULL DEFAULT 'unique',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_papers_title_normalized (title_normalized),
    KEY idx_papers_duplicate_status (duplicate_status),
    CONSTRAINT fk_papers_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文件表
CREATE TABLE IF NOT EXISTS files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    paper_id BIGINT DEFAULT NULL,
    file_type VARCHAR(16) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    file_hash VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_files_file_type (file_type),
    KEY idx_files_file_hash (file_hash),
    KEY idx_files_type_hash (file_type, file_hash),
    CONSTRAINT fk_files_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_files_paper FOREIGN KEY (paper_id) REFERENCES papers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 访问日志表
CREATE TABLE IF NOT EXISTS access_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ip_address VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL DEFAULT 'download',
    file_name VARCHAR(255) NOT NULL DEFAULT '',
    file_type VARCHAR(16) NOT NULL DEFAULT '',
    report_id BIGINT DEFAULT NULL,
    report_student_name VARCHAR(128) DEFAULT NULL,
    report_date DATE DEFAULT NULL,
    paper_title TEXT DEFAULT NULL,
    accessed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_access_logs_ip_address (ip_address),
    KEY idx_access_logs_action (action),
    KEY idx_access_logs_file_name (file_name),
    KEY idx_access_logs_file_type (file_type),
    KEY idx_access_logs_accessed_at (accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 成员表
CREATE TABLE IF NOT EXISTS members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 组会排期表
CREATE TABLE IF NOT EXISTS schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_date DATE NOT NULL,
    student_name VARCHAR(128) NOT NULL,
    topic TEXT DEFAULT NULL,
    meeting_format VARCHAR(32) NOT NULL DEFAULT '线下',
    location VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'upcoming',
    notes TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_schedules_meeting_date (meeting_date),
    KEY idx_schedules_student_name (student_name),
    KEY idx_schedules_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 论文推荐池表
CREATE TABLE IF NOT EXISTS paper_pool (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title TEXT NOT NULL,
    url VARCHAR(1024) DEFAULT NULL,
    recommended_by VARCHAR(128) NOT NULL,
    claimed_by VARCHAR(128) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'available',
    report_id BIGINT DEFAULT NULL,
    notes TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_paper_pool_recommended_by (recommended_by),
    KEY idx_paper_pool_claimed_by (claimed_by),
    KEY idx_paper_pool_status (status),
    CONSTRAINT fk_paper_pool_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 实验室文件表
CREATE TABLE IF NOT EXISTS lab_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    tags VARCHAR(512) NOT NULL DEFAULT '',
    original_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_hash VARCHAR(64) NOT NULL DEFAULT '',
    uploaded_by VARCHAR(128) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_lab_files_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SMTP 配置表 (单例, id 始终为 1)
CREATE TABLE IF NOT EXISTS smtp_config (
    id BIGINT NOT NULL,
    host VARCHAR(255) NOT NULL DEFAULT '',
    port INT NOT NULL DEFAULT 465,
    username VARCHAR(255) NOT NULL DEFAULT '',
    password VARCHAR(255) NOT NULL DEFAULT '',
    sender_name VARCHAR(128) NOT NULL DEFAULT '',
    use_tls TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
