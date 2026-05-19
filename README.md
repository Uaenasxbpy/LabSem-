# 文献管理系统（实验室内网版）

本项目使用 FastAPI + SQLite + Vue3 + Element Plus，实现文献汇报上传、智能检索与重复检测。

## 核心能力

- 一次提交：汇报人、汇报日期、论文列表（每篇标题+PDF）、汇报PPT
- 智能检索：按标题关键词、按人、按日期范围
- 重复检测（上传前拦截）
  - 标题规范化 + 模糊匹配
  - PDF 文件 SHA-256 指纹匹配
- 发现重复时，提示：
  - `警告！该论文已于 yyyy-mm-dd 由 xxx 同学汇报过，是否确认重复上传？`

## 数据与文件落地

- 数据库：`SQLite`（`data/literature.db`）
- 文件目录：`uploads/`
- 每次汇报自动创建唯一文件夹：`YYYY-MM-DD_学生姓名[_序号]`
- 文件重命名存储：`paper_1.pdf`, `paper_2.pdf`, `report_slides.pptx`

## 启动

```bash
docker compose up -d --build
```

访问：`http://localhost:8000`

## API

- `POST /api/reports` 上传汇报（支持重复确认）
  - `student_name`, `report_date`
  - `paper_titles`（可重复字段）
  - `paper_pdfs`（与标题一一对应）
  - `ppt_file`（可选）
  - `confirm_duplicate`（默认 false）
- `GET /api/reports` 检索
  - `keyword`, `student_name`, `start_date`, `end_date`
- `GET /api/students` 学生列表
- `GET /api/files/{file_id}/download` 下载文件

## 目录

```text
app/
  main.py
  database.py
  models.py
  services.py
  schemas.py
templates/
  index.html
static/
  app.js
  style.css
```
