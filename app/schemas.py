from datetime import date, datetime

from pydantic import BaseModel


class DuplicateHint(BaseModel):
    paper_title: str
    student_name: str
    report_date: date
    match_type: str
    score: float


class FileOut(BaseModel):
    id: int
    file_type: str
    original_name: str
    paper_id: int | None
    created_at: datetime

    class Config:
        from_attributes = True


class PaperOut(BaseModel):
    id: int
    title_raw: str
    duplicate_status: str

    class Config:
        from_attributes = True


class ReportOut(BaseModel):
    id: int
    student_name: str
    report_date: date
    folder_name: str
    papers: list[PaperOut]
    files: list[FileOut]

    class Config:
        from_attributes = True


class StudentOut(BaseModel):
    id: int
    name: str

    class Config:
        from_attributes = True


class StudentsOut(BaseModel):
    students: list[StudentOut]


class AccessLogOut(BaseModel):
    id: int
    ip_address: str
    action: str
    file_name: str
    file_type: str
    report_id: int | None
    report_student_name: str | None
    report_date: date | None
    paper_title: str | None
    accessed_at: datetime

    class Config:
        from_attributes = True


class MemberOut(BaseModel):
    id: int
    name: str
    email: str
    created_at: datetime

    class Config:
        from_attributes = True


class MembersOut(BaseModel):
    members: list[MemberOut]


class SmtpConfigOut(BaseModel):
    id: int
    host: str
    port: int
    username: str
    password_masked: str
    sender_name: str
    use_tls: bool


class ScheduleOut(BaseModel):
    id: int
    meeting_date: date
    student_name: str
    topic: str | None
    meeting_format: str
    location: str
    status: str
    notes: str | None
    created_at: datetime

    class Config:
        from_attributes = True


class SchedulesOut(BaseModel):
    schedules: list[ScheduleOut]


class PaperPoolOut(BaseModel):
    id: int
    title: str
    url: str | None
    recommended_by: str
    claimed_by: str | None
    status: str
    report_id: int | None
    notes: str | None
    created_at: datetime

    class Config:
        from_attributes = True


class PaperPoolListOut(BaseModel):
    papers: list[PaperPoolOut]


class LabFileOut(BaseModel):
    id: int
    title: str
    description: str | None
    tags: str
    original_name: str
    file_size: int
    uploaded_by: str | None
    created_at: datetime

    class Config:
        from_attributes = True


class LabFilesOut(BaseModel):
    files: list[LabFileOut]
