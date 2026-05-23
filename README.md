# 文献管理系统（实验室内网版）

基于 Spring Boot + Vue 3 + MySQL 的实验室文献汇报管理系统，支持文献上传、智能检索、重复检测、组会排期、论文推荐池、邮件通知等功能。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 + Spring Boot 3.4 + MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |
| 前端 | Vue 3 (setup) + Vite + Element Plus + Pinia + Vue Router |
| 部署 | Docker + docker-compose (Java + MySQL + Nginx) |

## 核心能力

- 一次提交：汇报人、汇报日期、论文列表（每篇标题+PDF）、汇报PPT
- 智能检索：按标题关键词、按人、按日期范围
- 重复检测（上传前拦截）
  - 标题规范化 + 模糊匹配（JaroWinkler，阈值 0.9）
  - PDF 文件 SHA-256 指纹匹配
- 发现重复时，提示：`警告！该论文已于 yyyy-mm-dd 由 xxx 同学汇报过，是否确认重复上传？`
- 文件在线预览（PDF）与下载
- 访问日志审计（记录每次下载/预览的 IP、时间、文件信息）
- 组会排期管理、论文推荐池、实验室文件管理
- 邮件通知（SMTP 配置 + 自定义邮件发送）
- 数据看板（统计报表、月度趋势、学生排名）

## 数据模型

| 表 | 说明 |
|----|------|
| `students` | 学生名录 |
| `reports` | 汇报记录，关联学生 |
| `papers` | 论文，属于某次汇报 |
| `files` | 存储文件（PDF/PPT），关联汇报和论文 |
| `access_logs` | 文件访问日志 |
| `members` | 实验室成员（邮件收件人） |
| `schedules` | 组会排期 |
| `paper_pool` | 论文推荐池 |
| `lab_files` | 实验室通用文件 |
| `smtp_config` | SMTP 邮件配置 |

## 项目结构

```
backend/
├── src/main/java/com/labsem/
│   ├── controller/       # 控制层 (12 个控制器, 35 个接口)
│   ├── service/          # 业务层 (12 个服务)
│   ├── mapper/           # 数据层 (MyBatis-Plus, 11 个 Mapper)
│   ├── entity/           # 实体类 (10 个)
│   ├── dto/              # 数据传输对象
│   ├── config/           # 配置类 (CORS, MyBatis-Plus)
│   ├── common/           # 通用层 (全局异常处理, 工具类)
│   └── constant/         # 常量
├── src/main/resources/
│   ├── application.yml        # 主配置
│   ├── application-dev.yml    # 开发环境
│   ├── application-prod.yml   # 生产环境
│   ├── db/schema.sql          # MySQL 建表脚本
│   └── mapper/                # MyBatis XML 映射
├── pom.xml
└── Dockerfile                 # 多阶段构建 (Maven + JRE)

frontend/
├── src/
│   ├── api/              # Axios 请求封装 (12 个模块)
│   ├── components/       # 公共组件 (7 个)
│   ├── views/            # 页面视图 (10 个)
│   ├── router/           # 路由配置 (懒加载)
│   ├── stores/           # Pinia 状态管理
│   └── styles/           # 样式 (CSS 变量 + Flat Design)
├── index.html
├── vite.config.js        # Vite 配置 + API 代理
├── package.json
├── Dockerfile            # 多阶段构建 (Node + Nginx)
└── nginx.conf            # 反向代理 + 静态资源缓存

docker-compose.yml        # 三容器编排
.env.example              # 环境变量示例
```

## 启动

### Docker 部署（推荐）

```bash
# 克隆项目
git clone <repo-url> && cd LabSem-

# 配置环境变量
cp .env.example .env
# 编辑 .env 设置 MySQL 密码

# 构建并启动所有服务
docker compose up -d --build

# 查看日志
docker compose logs -f
```

访问：`http://localhost:20766`

服务说明：
| 容器 | 端口 | 说明 |
|------|------|------|
| `labsem_frontend` | 20766 | Nginx 反向代理 + 前端静态文件 |
| `labsem_backend` | 8080 | Spring Boot API 服务 |
| `labsem_mysql` | 3306 | MySQL 8.0 数据库 |

### 本地开发

**前置要求：**
- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

**后端：**
```bash
cd backend

# 创建数据库
mysql -u root -p -e "CREATE DATABASE labsem CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入表结构
mysql -u root -p labsem < src/main/resources/db/schema.sql

# 启动 (默认连接 localhost:3306, 用户名 root, 密码 root)
mvn spring-boot:run
```

**前端：**
```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:3000`（自动代理到后端 8080）

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | `labsem123` | MySQL root 密码 |
| `DB_HOST` | `mysql` | 数据库主机 (Docker 内) |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | - | 数据库密码 |
| `UPLOAD_DIR` | `/app/uploads` | 文件上传目录 |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring 环境 (dev/prod) |

## API

所有接口路径与原 Python 版本完全兼容，支持 `multipart/form-data` 和 `application/json`。

### 汇报管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/reports` | 创建汇报（支持重复确认） |
| `GET` | `/api/reports` | 检索汇报 |
| `DELETE` | `/api/reports/{id}` | 删除汇报及关联文件 |

### 学生

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/students` | 获取学生列表 |

### 组会排期

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/schedules` | 查询排期 |
| `POST` | `/api/schedules` | 创建排期 |
| `PUT` | `/api/schedules/{id}` | 更新排期 |
| `DELETE` | `/api/schedules/{id}` | 删除排期 |
| `PUT` | `/api/schedules/{id}/status` | 更新排期状态 |

### 论文推荐池

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/paper-pool` | 查询论文池 |
| `POST` | `/api/paper-pool` | 添加论文 |
| `PUT` | `/api/paper-pool/{id}` | 更新论文 |
| `DELETE` | `/api/paper-pool/{id}` | 删除论文 |
| `PUT` | `/api/paper-pool/{id}/claim` | 认领论文 |
| `PUT` | `/api/paper-pool/{id}/unclaim` | 取消认领 |

### 文件

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/files` | 获取文件列表 |
| `GET` | `/api/files/{id}/preview` | 在线预览文件 |
| `GET` | `/api/files/{id}/download` | 下载文件 |

### 实验室文件

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/lab-files` | 查询文件 |
| `GET` | `/api/lab-files/tags` | 获取标签列表 |
| `POST` | `/api/lab-files` | 上传文件 |
| `PUT` | `/api/lab-files/{id}` | 更新文件信息 |
| `DELETE` | `/api/lab-files/{id}` | 删除文件 |
| `GET` | `/api/lab-files/{id}/download` | 下载文件 |
| `GET` | `/api/lab-files/{id}/preview` | 预览文件 |

### 成员管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/members` | 获取成员列表 |
| `POST` | `/api/members` | 添加成员 |
| `PUT` | `/api/members/{id}` | 更新成员 |
| `DELETE` | `/api/members/{id}` | 删除成员 |

### 邮件

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/smtp-config` | 获取 SMTP 配置 |
| `PUT` | `/api/smtp-config` | 更新 SMTP 配置 |
| `POST` | `/api/reports/{id}/notify` | 发送汇报通知 |
| `POST` | `/api/emails/send` | 发送自定义邮件 |

### 统计与日志

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/dashboard/stats` | 总体统计 |
| `GET` | `/api/dashboard/by-student` | 按学生统计 |
| `GET` | `/api/dashboard/monthly` | 月度统计 |
| `GET` | `/api/logs` | 访问日志 |

### 管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/admin/cleanup-stale-files` | 清理孤立文件记录 |
| `POST` | `/api/admin/sync-uploads` | 同步上传目录到数据库 |
| `GET` | `/health` | 健康检查 |

## 数据迁移

从旧版 SQLite 迁移数据：

```bash
# 导出 SQLite 数据
sqlite3 data/literature.db ".dump" > dump.sql

# 转换并导入到 MySQL (需要手动调整语法)
# 主要改动: AUTOINCREMENT → AUTO_INCREMENT, datetime → DATETIME
mysql -u root -p labsem < dump.sql
```

## 许可证

内部项目，仅供实验室使用。
