import base64
import hashlib
import os
import re
import smtplib
import tempfile
import unicodedata
import zipfile
from collections import defaultdict
from datetime import date
from difflib import SequenceMatcher
from email.header import Header
from email.mime.application import MIMEApplication
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr
from pathlib import Path

from sqlalchemy.orm import Session

from app import models
from app.models import SmtpConfig, Student


def normalize_title(title: str) -> str:
    normalized = unicodedata.normalize("NFKC", title).lower().strip()
    return re.sub(r"[\s\-_,.;:!?()\[\]{}<>/\\\"']+", "", normalized)


def find_or_create_student(db: Session, name: str) -> Student:
    normalized = unicodedata.normalize("NFKC", name).strip()
    student = db.query(Student).filter(Student.name == normalized).first()
    if not student:
        student = Student(name=normalized)
        db.add(student)
        db.flush()
    return student


def slugify_student_name(name: str) -> str:
    safe = unicodedata.normalize("NFKC", name).strip()
    safe = re.sub(r"[\\/:*?\"<>|]+", "_", safe)
    safe = re.sub(r"\s+", "_", safe)
    return safe or "unknown"


def ensure_storage_root() -> Path:
    root = Path(os.getenv("UPLOAD_DIR", "uploads"))
    root.mkdir(parents=True, exist_ok=True)
    return root


def create_unique_report_folder(report_date: date, student_name: str) -> tuple[str, Path]:
    root = ensure_storage_root()
    base = f"{report_date.isoformat()}_{slugify_student_name(student_name)}"
    folder_name = base
    index = 1
    while (root / folder_name).exists():
        folder_name = f"{base}_{index}"
        index += 1
    folder_path = root / folder_name
    folder_path.mkdir(parents=True, exist_ok=True)
    return folder_name, folder_path


def digest_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def rename_for_storage(file_type: str, index: int, original_name: str) -> str:
    suffix = Path(original_name).suffix.lower() or (".pdf" if file_type == "pdf" else ".pptx")
    if file_type == "pdf":
        return f"paper_{index + 1}{suffix}"
    return f"report_slides{suffix}"


def store_file(folder_path: Path, storage_name: str, content: bytes) -> str:
    target = folder_path / storage_name
    with target.open("wb") as f:
        f.write(content)
    return str(target)


def find_title_duplicates(db: Session, title: str, threshold: float = 0.9) -> list[dict]:
    normalized = normalize_title(title)
    duplicates: list[dict] = []

    papers = db.query(models.Paper).join(models.Report).all()
    for paper in papers:
        if not paper.title_normalized:
            continue
        if paper.title_normalized == normalized:
            duplicates.append(
                {
                    "paper_title": paper.title_raw,
                    "student_name": paper.report.student_name,
                    "report_date": paper.report.report_date,
                    "match_type": "title_exact",
                    "score": 1.0,
                }
            )
            continue

        score = SequenceMatcher(None, normalized, paper.title_normalized).ratio()
        if score >= threshold:
            duplicates.append(
                {
                    "paper_title": paper.title_raw,
                    "student_name": paper.report.student_name,
                    "report_date": paper.report.report_date,
                    "match_type": "title_similar",
                    "score": round(score, 4),
                }
            )

    duplicates.sort(key=lambda x: x["score"], reverse=True)
    return duplicates


def find_pdf_hash_duplicates(db: Session, file_hash: str) -> list[dict]:
    rows = (
        db.query(models.StoredFile)
        .join(models.Report, models.StoredFile.report_id == models.Report.id)
        .outerjoin(models.Paper, models.StoredFile.paper_id == models.Paper.id)
        .filter(models.StoredFile.file_type == "pdf", models.StoredFile.file_hash == file_hash)
        .all()
    )

    duplicates = []
    for row in rows:
        duplicates.append(
            {
                "paper_title": row.paper.title_raw if row.paper else row.original_name,
                "student_name": row.report.student_name,
                "report_date": row.report.report_date,
                "match_type": "file_hash",
                "score": 1.0,
            }
        )
    return duplicates


def encode_password(plain: str) -> str:
    return base64.b64encode(plain.encode("utf-8")).decode("ascii")


def decode_password(encoded: str) -> str:
    try:
        return base64.b64decode(encoded.encode("ascii")).decode("utf-8")
    except Exception:
        return encoded


def mask_password(_: str) -> str:
    return "******"


def get_smtp_config(db: Session) -> SmtpConfig | None:
    return db.query(SmtpConfig).filter(SmtpConfig.id == 1).first()


def send_smtp_email(
    config: SmtpConfig,
    recipients: list[str],
    subject: str,
    body: str,
    attachment_paths: list[tuple[str, str]] | None = None,
) -> dict:
    """发送邮件。attachment_paths = [(storage_path, original_name), ...]"""
    msg = MIMEMultipart()
    if config.sender_name:
        msg["From"] = formataddr((str(Header(config.sender_name, "utf-8")), config.username))
    else:
        msg["From"] = config.username
    msg["To"] = ", ".join(recipients)
    msg["Subject"] = subject
    msg.attach(MIMEText(body, "plain", "utf-8"))

    for storage_path, original_name in (attachment_paths or []):
        if not os.path.exists(storage_path):
            continue
        with open(storage_path, "rb") as f:
            part = MIMEApplication(f.read(), Name=original_name)
        part["Content-Disposition"] = f'attachment; filename="{original_name}"'
        msg.attach(part)

    password = decode_password(config.password)

    try:
        if config.use_tls:
            server = smtplib.SMTP_SSL(config.host, config.port, timeout=30)
        else:
            server = smtplib.SMTP(config.host, config.port, timeout=30)
            server.starttls()
        server.login(config.username, password)
        server.sendmail(config.username, recipients, msg.as_string())
        server.quit()
    except Exception as e:
        return {"ok": False, "error": f"邮件发送失败: {e}"}

    return {"ok": True, "message": f"已成功发送给 {len(recipients)} 位成员"}


MAX_EMAIL_ATTACHMENT_SIZE = 45 * 1024 * 1024  # 45MB


def _compress_files_by_report(files: list[models.StoredFile]) -> tuple[list[tuple[str, str]], list[str]]:
    """按汇报记录分组压缩文件。返回 (attachments, temp_dirs_to_clean)。
    如果压缩后仍超限，抛出 ValueError。"""
    total_size = sum(
        os.path.getsize(f.storage_path) for f in files if os.path.exists(f.storage_path)
    )
    if total_size <= MAX_EMAIL_ATTACHMENT_SIZE:
        return [(f.storage_path, f.original_name) for f in files if os.path.exists(f.storage_path)], []

    groups: dict[int, list[models.StoredFile]] = defaultdict(list)
    for f in files:
        groups[f.report_id].append(f)

    temp_dir = tempfile.mkdtemp(prefix="labsem_mail_")
    attachments = []

    for report_id, group_files in groups.items():
        sample = group_files[0]
        report = sample.report
        folder_name = report.folder_name if report else f"report_{report_id}"
        zip_name = f"{folder_name}.zip"
        zip_path = os.path.join(temp_dir, zip_name)

        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for f in group_files:
                if os.path.exists(f.storage_path):
                    zf.write(f.storage_path, f.original_name)

        attachments.append((zip_path, zip_name))

    # Check if any single zip is too large
    oversized = []
    for zip_path, zip_name in attachments:
        if os.path.getsize(zip_path) > MAX_EMAIL_ATTACHMENT_SIZE:
            oversized.append(f"{zip_name}（{round(os.path.getsize(zip_path) / 1024 / 1024, 1)}MB）")
    if oversized:
        _cleanup_temp_dirs([temp_dir])
        raise ValueError(
            f"以下汇报的文件即使压缩后仍超出 45MB 限制，无法发送：{'、'.join(oversized)}。"
            f"请检查是否有不必要的大文件。"
        )

    return attachments, [temp_dir]


def _cleanup_temp_dirs(temp_dirs: list[str]) -> None:
    import shutil
    for d in temp_dirs:
        shutil.rmtree(d, ignore_errors=True)


def _split_attachments(attachments: list[tuple[str, str]]) -> list[list[tuple[str, str]]]:
    """将附件按 45MB 上限拆分成多批。"""
    batches = []
    current_batch = []
    current_size = 0

    for path, name in attachments:
        fsize = os.path.getsize(path)
        if current_size + fsize > MAX_EMAIL_ATTACHMENT_SIZE and current_batch:
            batches.append(current_batch)
            current_batch = []
            current_size = 0
        current_batch.append((path, name))
        current_size += fsize

    if current_batch:
        batches.append(current_batch)
    return batches


def send_report_notification(
    db: Session,
    report: models.Report,
    papers: list[models.Paper],
    files: list[models.StoredFile],
    recipients: list[str],
) -> dict:
    config = get_smtp_config(db)
    if not config or not config.host:
        return {"ok": False, "error": "未配置SMTP信息，请先在邮件设置中配置"}

    if not recipients:
        return {"ok": False, "error": "收件人列表为空，请先添加实验室成员"}

    subject = f"文献汇报通知 - {report.student_name} - {report.report_date}"

    paper_lines = "\n".join(
        f"  {i + 1}. {p.title_raw}" for i, p in enumerate(papers)
    )
    body = (
        f"各位好，\n\n"
        f"{report.student_name} 同学于 {report.report_date} 进行了文献汇报，"
        f"汇报论文如下：\n\n{paper_lines}\n\n"
        f"请查收附件中的材料。\n\n"
        f"—— 实验室文献管理系统"
    )

    try:
        attachments, temp_dirs = _compress_files_by_report(files)
    except ValueError as e:
        return {"ok": False, "error": str(e)}

    batches = _split_attachments(attachments)
    total_sent = 0
    for i, batch in enumerate(batches):
        batch_subject = subject if len(batches) == 1 else f"{subject}（{i + 1}/{len(batches)}）"
        result = send_smtp_email(config, recipients, batch_subject, body, batch)
        if not result["ok"]:
            _cleanup_temp_dirs(temp_dirs)
            return result
        total_sent += len(batch)

    _cleanup_temp_dirs(temp_dirs)
    return {"ok": True, "message": f"已成功发送给 {len(recipients)} 位成员（共 {len(batches)} 封邮件）"}


def send_custom_email(
    db: Session,
    recipients: list[str],
    subject: str,
    body: str,
    files: list[models.StoredFile],
) -> dict:
    config = get_smtp_config(db)
    if not config or not config.host:
        return {"ok": False, "error": "未配置SMTP信息，请先在邮件设置中配置"}

    if not recipients:
        return {"ok": False, "error": "收件人列表为空"}

    try:
        attachments, temp_dirs = _compress_files_by_report(files)
    except ValueError as e:
        return {"ok": False, "error": str(e)}

    batches = _split_attachments(attachments)
    for i, batch in enumerate(batches):
        batch_subject = subject if len(batches) == 1 else f"{subject}（{i + 1}/{len(batches)}）"
        result = send_smtp_email(config, recipients, batch_subject, body, batch)
        if not result["ok"]:
            _cleanup_temp_dirs(temp_dirs)
            return result

    _cleanup_temp_dirs(temp_dirs)
    return {"ok": True, "message": f"已成功发送给 {len(recipients)} 位成员（共 {len(batches)} 封邮件）"}
    return send_smtp_email(config, recipients, subject, body, attachments)
