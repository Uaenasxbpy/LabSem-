import mimetypes
import os
import re
import shutil
from datetime import date

from fastapi import Body, Depends, FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, HTMLResponse
from pydantic import BaseModel
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy import exists, select
from sqlalchemy.orm import Session, joinedload

from app.database import Base, engine, get_db
from app.models import AccessLog, Member, Paper, Report, SmtpConfig, StoredFile, Student
from app.schemas import AccessLogOut, MemberOut, MembersOut, ReportOut, SmtpConfigOut, StudentsOut
from app.services import (
    create_unique_report_folder,
    decode_password,
    digest_bytes,
    encode_password,
    ensure_storage_root,
    find_or_create_student,
    find_pdf_hash_duplicates,
    find_title_duplicates,
    get_smtp_config,
    mask_password,
    normalize_title,
    rename_for_storage,
    send_custom_email,
    send_report_notification,
    store_file,
)

app = FastAPI(title="文献管理系统", version="0.2.5")

app.mount("/static", StaticFiles(directory="static"), name="static")
templates = Jinja2Templates(directory="templates")


@app.on_event("startup")
def startup_event() -> None:
    Base.metadata.create_all(bind=engine)


@app.get("/", response_class=HTMLResponse)
def index(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.get("/api/students", response_model=StudentsOut)
def list_students(db: Session = Depends(get_db)):
    rows = db.query(Student).order_by(Student.name.asc()).all()
    return {"students": rows}


@app.post("/api/reports")
async def create_report(
    student_name: str = Form(..., min_length=1),
    report_date: date = Form(...),
    paper_titles: list[str] = Form(...),
    paper_pdfs: list[UploadFile] = File(...),
    ppt_file: UploadFile | None = File(default=None),
    confirm_duplicate: bool = Form(default=False),
    db: Session = Depends(get_db),
):
    clean_titles = [title.strip() for title in paper_titles if title.strip()]
    if not clean_titles:
        raise HTTPException(status_code=400, detail="至少需要填写一篇论文标题")
    if len(paper_pdfs) != len(clean_titles):
        raise HTTPException(status_code=400, detail="每篇论文需要对应一个PDF文件")

    pdf_payloads: list[tuple[UploadFile, bytes, str]] = []
    duplicate_hints = []

    for idx, title in enumerate(clean_titles):
        file_obj = paper_pdfs[idx]
        content = await file_obj.read()
        if not content:
            raise HTTPException(status_code=400, detail=f"第 {idx + 1} 篇论文PDF为空")

        file_hash = digest_bytes(content)
        pdf_payloads.append((file_obj, content, file_hash))

        for hint in find_title_duplicates(db, title):
            duplicate_hints.append({"input_title": title, **hint})
        for hint in find_pdf_hash_duplicates(db, file_hash):
            duplicate_hints.append({"input_title": title, **hint})

    if duplicate_hints and not confirm_duplicate:
        first = duplicate_hints[0]
        warning = (
            f"警告！该论文已于 {first['report_date']} 由 {first['student_name']} 同学汇报过，是否确认重复上传？"
        )
        return {
            "ok": False,
            "need_confirm": True,
            "warning": warning,
            "duplicates": duplicate_hints,
        }

    folder_name, folder_path = create_unique_report_folder(report_date, student_name)

    student = find_or_create_student(db, student_name)

    report = Report(
        student_id=student.id,
        student_name=student.name,
        report_date=report_date,
        folder_name=folder_name,
    )
    db.add(report)
    db.flush()

    for idx, title in enumerate(clean_titles):
        has_dup = any(item["input_title"] == title for item in duplicate_hints)
        paper = Paper(
            report_id=report.id,
            title_raw=title,
            title_normalized=normalize_title(title),
            duplicate_status="duplicate" if has_dup else "unique",
        )
        db.add(paper)
        db.flush()

        upload_file, content, file_hash = pdf_payloads[idx]
        storage_name = rename_for_storage("pdf", idx, upload_file.filename or f"paper_{idx + 1}.pdf")
        storage_path = store_file(folder_path, storage_name, content)

        db.add(
            StoredFile(
                report_id=report.id,
                paper_id=paper.id,
                file_type="pdf",
                original_name=upload_file.filename or storage_name,
                storage_name=storage_name,
                storage_path=storage_path,
                file_hash=file_hash,
            )
        )

    if ppt_file is not None:
        ppt_content = await ppt_file.read()
        if ppt_content:
            ppt_hash = digest_bytes(ppt_content)
            ppt_name = rename_for_storage("ppt", 0, ppt_file.filename or "report_slides.pptx")
            ppt_path = store_file(folder_path, ppt_name, ppt_content)
            db.add(
                StoredFile(
                    report_id=report.id,
                    paper_id=None,
                    file_type="ppt",
                    original_name=ppt_file.filename or ppt_name,
                    storage_name=ppt_name,
                    storage_path=ppt_path,
                    file_hash=ppt_hash,
                )
            )

    db.commit()

    return {
        "ok": True,
        "need_confirm": False,
        "report_id": report.id,
        "folder_name": folder_name,
        "duplicates": duplicate_hints,
    }


@app.get("/api/reports", response_model=list[ReportOut])
def search_reports(
    keyword: str | None = Query(default=None),
    student_name: str | None = Query(default=None),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=300),
    db: Session = Depends(get_db),
):
    query = db.query(Report).options(joinedload(Report.papers), joinedload(Report.files))

    if keyword:
        normalized_kw = normalize_title(keyword)
        paper_exists = (
            exists()
            .where(Paper.report_id == Report.id)
            .where((Paper.title_raw.contains(keyword)) | (Paper.title_normalized.contains(normalized_kw)))
        )
        query = query.filter(paper_exists)

    if student_name:
        query = query.filter(Report.student_name.contains(student_name.strip()))

    if start_date:
        query = query.filter(Report.report_date >= start_date)

    if end_date:
        query = query.filter(Report.report_date <= end_date)

    results = query.order_by(Report.report_date.desc(), Report.id.desc()).limit(limit).all()
    return results


@app.get("/api/logs", response_model=list[AccessLogOut])
def get_access_logs(
    ip: str | None = Query(default=None),
    keyword: str | None = Query(default=None),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    limit: int = Query(default=200, ge=1, le=500),
    db: Session = Depends(get_db),
):
    query = db.query(AccessLog)

    if ip:
        query = query.filter(AccessLog.ip_address.contains(ip.strip()))

    if keyword:
        kw = keyword.strip()
        query = query.filter(
            AccessLog.file_name.contains(kw)
            | AccessLog.paper_title.contains(kw)
            | AccessLog.report_student_name.contains(kw)
        )

    if start_date:
        query = query.filter(AccessLog.report_date >= start_date)

    if end_date:
        query = query.filter(AccessLog.report_date <= end_date)

    return query.order_by(AccessLog.accessed_at.desc(), AccessLog.id.desc()).limit(limit).all()


@app.delete("/api/reports/{report_id}")
def delete_report(report_id: int, db: Session = Depends(get_db)):
    report = (
        db.query(Report)
        .options(joinedload(Report.files), joinedload(Report.papers))
        .filter(Report.id == report_id)
        .first()
    )
    if not report:
        raise HTTPException(status_code=404, detail="汇报记录不存在")

    for file_row in list(report.files):
        if os.path.exists(file_row.storage_path):
            os.remove(file_row.storage_path)

    folder = ensure_storage_root() / report.folder_name
    if folder.exists() and folder.is_dir():
        shutil.rmtree(folder, ignore_errors=True)

    db.delete(report)
    db.commit()

    return {"ok": True, "message": "已删除整条汇报记录"}


@app.post("/api/admin/cleanup-stale-files")
def cleanup_stale_files(db: Session = Depends(get_db)):
    all_files = db.query(StoredFile).all()
    removed = 0
    for file_row in all_files:
        if not os.path.exists(file_row.storage_path):
            db.delete(file_row)
            removed += 1
    if removed:
        db.commit()
    return {"ok": True, "removed": removed}


@app.post("/api/admin/sync-uploads")
def sync_uploads(db: Session = Depends(get_db)):
    root = ensure_storage_root()
    if not root.exists():
        return {"ok": True, "imported": 0, "skipped": 0, "details": []}

    imported = 0
    skipped = 0
    details = []

    for entry in sorted(root.iterdir()):
        if not entry.is_dir():
            continue

        folder_name = entry.name
        existing = db.query(Report).filter(Report.folder_name == folder_name).first()
        if existing:
            skipped += 1
            continue

        match = re.match(r"^(\d{4}-\d{2}-\d{2})_(.+)$", folder_name)
        if not match:
            skipped += 1
            continue

        report_date_str, student_name = match.group(1), match.group(2)
        report_date = date.fromisoformat(report_date_str)

        student = find_or_create_student(db, student_name)

        report = Report(
            student_id=student.id,
            student_name=student.name,
            report_date=report_date,
            folder_name=folder_name,
        )
        db.add(report)
        db.flush()

        paper_idx = 0
        for f in sorted(entry.iterdir()):
            if not f.is_file():
                continue

            suffix = f.suffix.lower()
            file_content = f.read_bytes()
            file_hash = digest_bytes(file_content)

            if suffix == ".pdf" and f.name.startswith("paper_"):
                paper = Paper(
                    report_id=report.id,
                    title_raw=f"论文 {paper_idx + 1}",
                    title_normalized=normalize_title(f"论文 {paper_idx + 1}"),
                    duplicate_status="unique",
                )
                db.add(paper)
                db.flush()

                db.add(
                    StoredFile(
                        report_id=report.id,
                        paper_id=paper.id,
                        file_type="pdf",
                        original_name=f.name,
                        storage_name=f.name,
                        storage_path=str(f),
                        file_hash=file_hash,
                    )
                )
                paper_idx += 1

            elif suffix in (".pptx", ".ppt"):
                db.add(
                    StoredFile(
                        report_id=report.id,
                        paper_id=None,
                        file_type="ppt",
                        original_name=f.name,
                        storage_name=f.name,
                        storage_path=str(f),
                        file_hash=file_hash,
                    )
                )
            elif suffix == ".pdf" and f.name.startswith("report_slides"):
                db.add(
                    StoredFile(
                        report_id=report.id,
                        paper_id=None,
                        file_type="ppt",
                        original_name=f.name,
                        storage_name=f.name,
                        storage_path=str(f),
                        file_hash=file_hash,
                    )
                )

        imported += 1
        details.append(f"{folder_name} ({paper_idx} 篇论文)")

    db.commit()
    return {"ok": True, "imported": imported, "skipped": skipped, "details": details}


# ---- 成员管理 ----

@app.get("/api/members", response_model=MembersOut)
def list_members(db: Session = Depends(get_db)):
    rows = db.query(Member).order_by(Member.name.asc()).all()
    return {"members": rows}


@app.post("/api/members", response_model=MemberOut)
def create_member(name: str = Form(..., min_length=1), email: str = Form(..., min_length=3), db: Session = Depends(get_db)):
    name = name.strip()
    email = email.strip().lower()
    if db.query(Member).filter(Member.email == email).first():
        raise HTTPException(status_code=400, detail="该邮箱已存在")
    member = Member(name=name, email=email)
    db.add(member)
    db.commit()
    db.refresh(member)
    return member


@app.put("/api/members/{member_id}", response_model=MemberOut)
def update_member(member_id: int, name: str = Form(..., min_length=1), email: str = Form(..., min_length=3), db: Session = Depends(get_db)):
    member = db.query(Member).filter(Member.id == member_id).first()
    if not member:
        raise HTTPException(status_code=404, detail="成员不存在")
    email = email.strip().lower()
    existing = db.query(Member).filter(Member.email == email, Member.id != member_id).first()
    if existing:
        raise HTTPException(status_code=400, detail="该邮箱已被其他成员使用")
    member.name = name.strip()
    member.email = email
    db.commit()
    db.refresh(member)
    return member


@app.delete("/api/members/{member_id}")
def delete_member(member_id: int, db: Session = Depends(get_db)):
    member = db.query(Member).filter(Member.id == member_id).first()
    if not member:
        raise HTTPException(status_code=404, detail="成员不存在")
    db.delete(member)
    db.commit()
    return {"ok": True}


# ---- SMTP 配置 ----

@app.get("/api/smtp-config", response_model=SmtpConfigOut)
def get_smtp_config_api(db: Session = Depends(get_db)):
    config = get_smtp_config(db)
    if not config:
        return SmtpConfigOut(id=0, host="", port=465, username="", password_masked="", sender_name="", use_tls=True)
    return SmtpConfigOut(
        id=config.id,
        host=config.host,
        port=config.port,
        username=config.username,
        password_masked=mask_password(config.password),
        sender_name=config.sender_name,
        use_tls=config.use_tls,
    )


@app.put("/api/smtp-config", response_model=SmtpConfigOut)
def update_smtp_config_api(
    host: str = Form(default=""),
    port: int = Form(default=465),
    username: str = Form(default=""),
    password: str = Form(default=""),
    sender_name: str = Form(default=""),
    use_tls: bool = Form(default=True),
    db: Session = Depends(get_db),
):
    config = get_smtp_config(db)
    if not config:
        config = SmtpConfig(id=1)
        db.add(config)
    config.host = host.strip()
    config.port = port
    config.username = username.strip()
    if password:
        config.password = encode_password(password)
    config.sender_name = sender_name.strip()
    config.use_tls = use_tls
    db.commit()
    db.refresh(config)
    return SmtpConfigOut(
        id=config.id,
        host=config.host,
        port=config.port,
        username=config.username,
        password_masked=mask_password(config.password),
        sender_name=config.sender_name,
        use_tls=config.use_tls,
    )


# ---- 邮件通知 ----

@app.post("/api/reports/{report_id}/notify")
def notify_report(report_id: int, db: Session = Depends(get_db)):
    report = (
        db.query(Report)
        .options(joinedload(Report.papers), joinedload(Report.files))
        .filter(Report.id == report_id)
        .first()
    )
    if not report:
        raise HTTPException(status_code=404, detail="汇报记录不存在")

    members = db.query(Member).all()
    recipients = [m.email for m in members]
    result = send_report_notification(db, report, list(report.papers), list(report.files), recipients)
    if not result["ok"]:
        raise HTTPException(status_code=500, detail=result["error"])
    return result


# ---- 文件列表（用于附件选择） ----

@app.get("/api/files")
def list_files(db: Session = Depends(get_db)):
    rows = (
        db.query(StoredFile)
        .options(joinedload(StoredFile.report), joinedload(StoredFile.paper))
        .order_by(StoredFile.id.desc())
        .all()
    )
    result = []
    for f in rows:
        size = 0
        if os.path.exists(f.storage_path):
            size = os.path.getsize(f.storage_path)
        result.append({
            "id": f.id,
            "original_name": f.original_name,
            "file_type": f.file_type,
            "file_size": size,
            "report_id": f.report_id,
            "report_student_name": f.report.student_name if f.report else None,
            "report_date": f.report.report_date.isoformat() if f.report else None,
            "paper_title": f.paper.title_raw if f.paper else None,
        })
    return result


# ---- 自定义邮件发送 ----

class EmailRequest(BaseModel):
    member_ids: list[int]
    file_ids: list[int]
    subject: str
    body: str


@app.post("/api/emails/send")
def send_custom_email_api(req: EmailRequest, db: Session = Depends(get_db)):
    if not req.subject.strip():
        raise HTTPException(status_code=400, detail="邮件主题不能为空")

    members = db.query(Member).filter(Member.id.in_(req.member_ids)).all()
    recipients = [m.email for m in members]
    if not recipients:
        raise HTTPException(status_code=400, detail="未选择收件人")

    files = []
    if req.file_ids:
        files = (
            db.query(StoredFile)
            .options(joinedload(StoredFile.report))
            .filter(StoredFile.id.in_(req.file_ids))
            .all()
        )

    result = send_custom_email(db, recipients, req.subject.strip(), req.body, files)
    if not result["ok"]:
        raise HTTPException(status_code=500, detail=result["error"])
    return result


def get_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("x-forwarded-for", "")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()

    if request.client and request.client.host:
        return request.client.host

    return "unknown"


def write_access_log(db: Session, request: Request, file_record: StoredFile, action: str) -> None:
    log_row = AccessLog(
        ip_address=get_client_ip(request),
        action=action,
        file_name=file_record.original_name,
        file_type=file_record.file_type,
        report_id=file_record.report_id,
        report_student_name=file_record.report.student_name if file_record.report else None,
        report_date=file_record.report.report_date if file_record.report else None,
        paper_title=file_record.paper.title_raw if file_record.paper else None,
    )
    db.add(log_row)
    db.commit()


def resolve_media_type(file_record: StoredFile) -> str:
    media_type, _ = mimetypes.guess_type(file_record.original_name)
    if media_type:
        return media_type

    ext = os.path.splitext(file_record.original_name)[1].lower()
    if ext == ".ppt":
        return "application/vnd.ms-powerpoint"
    if ext == ".pptx":
        return "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    return "application/octet-stream"


@app.get("/api/files/{file_id}/preview")
def preview_file(file_id: int, request: Request, db: Session = Depends(get_db)):
    file_record = (
        db.query(StoredFile)
        .options(joinedload(StoredFile.report), joinedload(StoredFile.paper))
        .filter(StoredFile.id == file_id)
        .first()
    )
    if not file_record:
        raise HTTPException(status_code=404, detail="文件不存在")

    if not os.path.exists(file_record.storage_path):
        db.delete(file_record)
        db.commit()
        raise HTTPException(status_code=404, detail="文件已不存在，记录已自动清理")

    write_access_log(db, request, file_record, action="preview")

    return FileResponse(
        file_record.storage_path,
        media_type=resolve_media_type(file_record),
        filename=file_record.original_name,
        content_disposition_type="inline",
    )


@app.get("/api/files/{file_id}/download")
def download_file(file_id: int, request: Request, db: Session = Depends(get_db)):
    file_record = (
        db.query(StoredFile)
        .options(joinedload(StoredFile.report), joinedload(StoredFile.paper))
        .filter(StoredFile.id == file_id)
        .first()
    )
    if not file_record:
        raise HTTPException(status_code=404, detail="文件不存在")

    if not os.path.exists(file_record.storage_path):
        db.delete(file_record)
        db.commit()
        raise HTTPException(status_code=404, detail="文件已不存在，记录已自动清理")

    write_access_log(db, request, file_record, action="download")

    return FileResponse(file_record.storage_path, filename=file_record.original_name)

