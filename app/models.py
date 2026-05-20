from datetime import date, datetime

from sqlalchemy import Date, DateTime, ForeignKey, Index, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Student(Base):
    __tablename__ = "students"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())

    reports: Mapped[list["Report"]] = relationship(back_populates="student")


class Report(Base):
    __tablename__ = "reports"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    student_id: Mapped[int] = mapped_column(ForeignKey("students.id", ondelete="RESTRICT"))
    student_name: Mapped[str] = mapped_column(String(128), index=True)
    report_date: Mapped[date] = mapped_column(Date, index=True)
    folder_name: Mapped[str] = mapped_column(String(255), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())

    student: Mapped["Student"] = relationship(back_populates="reports")
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
    files: Mapped[list["StoredFile"]] = relationship(
        back_populates="paper", cascade="all, delete-orphan"
    )


class StoredFile(Base):
    __tablename__ = "files"
    __table_args__ = (
        Index("ix_files_type_hash", "file_type", "file_hash"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"))
    paper_id: Mapped[int | None] = mapped_column(
        ForeignKey("papers.id", ondelete="CASCADE"), nullable=True
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


class Member(Base):
    __tablename__ = "members"

    id: Mapped[int] = mapped_column(primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(128))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class SmtpConfig(Base):
    __tablename__ = "smtp_config"

    id: Mapped[int] = mapped_column(primary_key=True)
    host: Mapped[str] = mapped_column(String(255), default="")
    port: Mapped[int] = mapped_column(default=465)
    username: Mapped[str] = mapped_column(String(255), default="")
    password: Mapped[str] = mapped_column(String(255), default="")
    sender_name: Mapped[str] = mapped_column(String(128), default="")
    use_tls: Mapped[bool] = mapped_column(default=True)
