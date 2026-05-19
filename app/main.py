import mimetypes
import os
import shutil
from datetime import date

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from app.database import Base, engine, get_db
from app.models import AccessLog, Paper, Report, StoredFile
from app.schemas import AccessLogOut, ReportOut, StudentsOut
from app.services import (
    create_unique_report_folder,
    digest_bytes,
    ensure_storage_root,
    find_pdf_hash_duplicates,
    find_title_duplicates,
    normalize_title,
    rename_for_storage,
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
    rows = db.execute(select(Report.student_name).distinct().order_by(Report.student_name.asc())).all()
    return {"students": [row[0] for row in rows]}


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

    report = Report(
        student_name=student_name.strip(),
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
        query = query.join(Paper, Paper.report_id == Report.id).filter(
            (Paper.title_raw.contains(keyword)) | (Paper.title_normalized.contains(normalized_kw))
        )

    if student_name:
        query = query.filter(Report.student_name.contains(student_name.strip()))

    if start_date:
        query = query.filter(Report.report_date >= start_date)

    if end_date:
        query = query.filter(Report.report_date <= end_date)

    results = query.order_by(Report.report_date.desc(), Report.id.desc()).limit(limit).all()

    stale_files: list[StoredFile] = []
    for report in results:
        alive_files = []
        for file_row in report.files:
            if os.path.exists(file_row.storage_path):
                alive_files.append(file_row)
            else:
                stale_files.append(file_row)
        report.files = alive_files

    if stale_files:
        for stale in stale_files:
            db.delete(stale)
        db.commit()

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

