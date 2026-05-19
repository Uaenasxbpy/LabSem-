# 文献管理系统（实验室内网版）

本项目使用 FastAPI + SQLite + Vue3 + Element Plus，实现文献汇报上传、智能检索与重复检测。前端采用 Flat Design 风格，Teal + Orange 配色方案。

## 核心能力

- 一次提交：汇报人、汇报日期、论文列表（每篇标题+PDF）、汇报PPT
- 智能检索：按标题关键词、按人、按日期范围
- 重复检测（上传前拦截）
  - 标题规范化 + 模糊匹配（SequenceMatcher，阈值 0.9）
  - PDF 文件 SHA-256 指纹匹配
- 发现重复时，提示：
  - `警告！该论文已于 yyyy-mm-dd 由 xxx 同学汇报过，是否确认重复上传？`
- 文件在线预览（PDF）与下载
- 访问日志审计（记录每次下载/预览的 IP、时间、文件信息）

## 数据模型

| 表 | 说明 |
|----|------|
| `students` | 学生名录（id, name, created_at） |
| `reports` | 汇报记录，关联学生（student_id FK） |
| `papers` | 论文，属于某次汇报（report_id FK） |
| `files` | 存储文件（PDF/PPT），关联汇报和论文 |
| `access_logs` | 文件访问日志 |

外键关系：
- `reports.student_id` → `students.id`（RESTRICT）
- `papers.report_id` → `reports.id`（CASCADE）
- `files.report_id` → `reports.id`（CASCADE）
- `files.paper_id` → `papers.id`（CASCADE）

## 启动

### Docker 部署

```bash
docker compose up -d --build
```

### 本地开发

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

访问：`http://localhost:8000`

## API

### 汇报管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/reports` | 创建汇报（支持重复确认） |
| `GET` | `/api/reports` | 检索汇报 |
| `DELETE` | `/api/reports/{id}` | 删除汇报及关联文件 |

`POST /api/reports` 参数：
- `student_name` — 汇报人姓名
- `report_date` — 汇报日期
- `paper_titles` — 论文标题列表（可重复字段）
- `paper_pdfs` — PDF 文件列表（与标题一一对应）
- `ppt_file` — 汇报 PPT（可选）
- `confirm_duplicate` — 是否确认重复上传（默认 false）

`GET /api/reports` 参数：
- `keyword` — 论文标题关键词
- `student_name` — 汇报人
- `start_date` / `end_date` — 日期范围
- `limit` — 返回数量上限（默认 100，最大 300）

### 学生

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/students` | 获取学生列表 |

### 文件

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/files/{id}/preview` | 在线预览文件 |
| `GET` | `/api/files/{id}/download` | 下载文件 |

### 日志与维护

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/logs` | 查询访问日志（支持 ip/keyword/日期过滤） |
| `POST` | `/api/admin/cleanup-stale-files` | 清理磁盘上已不存在的孤立文件记录 |

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/health` | 服务状态检查 |

## 目录结构

```text
app/
  main.py          # 路由 + 业务逻辑
  database.py      # 数据库连接配置
  models.py        # SQLAlchemy 数据模型
  schemas.py       # Pydantic 响应模型
  services.py      # 工具函数（文件存储、查重、学生管理）
templates/
  index.html       # Jinja2 页面模板
static/
  app.js           # Vue 3 前端逻辑
  style.css        # Flat Design 样式（Teal + Orange 配色）
data/              # SQLite 数据库文件（gitignore）
uploads/           # 上传文件存储（gitignore）
Dockerfile
docker-compose.yml
requirements.txt
```

## 技术栈

- **后端**：FastAPI 0.115 + SQLAlchemy 2.0 + SQLite
- **前端**：Vue 3 + Element Plus（CDN）
- **字体**：Crimson Pro（标题）+ Atkinson Hyperlegible（正文）
- **部署**：Docker + docker-compose
