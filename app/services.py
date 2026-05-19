import hashlib
import os
import re
import unicodedata
from datetime import date
from difflib import SequenceMatcher
from pathlib import Path

from sqlalchemy.orm import Session

from app import models
from app.models import Student


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
