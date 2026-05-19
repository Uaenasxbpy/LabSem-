from datetime import date, datetime

from sqlalchemy import Date, DateTime, ForeignKey, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Report(Base):
    __tablename__ = "reports"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    student_name: Mapped[str] = mapped_column(String(128), index=True)
    report_date: Mapped[date] = mapped_column(Date, index=True)
    folder_name: Mapped[str] = mapped_column(String(255), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())

    papers: Mapped[list["Paper"]] = relationship(
        back_populates="report", cascade="all, delete-orphan"
    )
    files: Mapped[list["StoredFile"]] = relationship(
        back_populates="report", cascade="all, delete-orphan"
    )


class Paper(Base):
    __tablename__ = "papers"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"))
    title_raw: Mapped[str] = mapped_column(Text)
    title_normalized: Mapped[str] = mapped_column(String(512), index=True)
    duplicate_status: Mapped[str] = mapped_column(String(32), default="unique", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())

    report: Mapped["Report"] = relationship(back_populates="papers")
    files: Mapped[list["StoredFile"]] = relationship(back_populates="paper")


class StoredFile(Base):
    __tablename__ = "files"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"))
    paper_id: Mapped[int | None] = mapped_column(
        ForeignKey("papers.id", ondelete="SET NULL"), nullable=True
    )
    file_type: Mapped[str] = mapped_column(String(16), index=True)
    original_name: Mapped[str] = mapped_column(String(255))
    storage_name: Mapped[str] = mapped_column(String(255))
    storage_path: Mapped[str] = mapped_column(String(1024))
    file_hash: Mapped[str] = mapped_column(String(64), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())

    report: Mapped["Report"] = relationship(back_populates="files")
    paper: Mapped["Paper | None"] = relationship(back_populates="files")


class AccessLog(Base):
    __tablename__ = "access_logs"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    ip_address: Mapped[str] = mapped_column(String(64), index=True)
    action: Mapped[str] = mapped_column(String(32), default="download", index=True)
    file_name: Mapped[str] = mapped_column(String(255), index=True)
    file_type: Mapped[str] = mapped_column(String(16), index=True)
    report_id: Mapped[int | None] = mapped_column(nullable=True)
    report_student_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    report_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    paper_title: Mapped[str | None] = mapped_column(Text, nullable=True)
    accessed_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), index=True)
