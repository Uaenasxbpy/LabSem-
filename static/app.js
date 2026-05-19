const { createApp, reactive, ref, onMounted } = Vue;
const { ElMessage, ElMessageBox } = ElementPlus;

createApp({
  setup() {
    const uploadLoading = ref(false);
    const searchLoading = ref(false);
    const logsLoading = ref(false);
    const cleanupLoading = ref(false);
    const students = ref([]);
    const reports = ref([]);
    const logs = ref([]);
    const activeNav = ref("upload");

    const previewVisible = ref(false);
    const previewUrl = ref("");
    const previewTitle = ref("");
    const previewHint = ref("");

    const uploadForm = reactive({
      studentName: "",
      reportDate: new Date(),
      papers: [{ title: "", file: null }],
      pptFile: null,
    });

    const searchForm = reactive({
      keyword: "",
      studentName: "",
      dateRange: [],
    });

    const logForm = reactive({
      ip: "",
      keyword: "",
      dateRange: [],
    });

    const safeTrim = (value) => String(value ?? "").trim();

    const normalizeDate = (d) => {
      if (!d) return "";
      if (typeof d === "string") return d;
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    };

    const fileExt = (name) => {
      const parts = String(name || "").split(".");
      return parts.length > 1 ? parts.pop().toLowerCase() : "";
    };

    const openPreview = (file) => {
      if (!file || !file.id) return;
      previewTitle.value = file.original_name || "文件预览";
      previewUrl.value = `/api/files/${file.id}/preview`;

      const ext = fileExt(file.original_name);
      if (ext === "ppt" || ext === "pptx") {
        previewHint.value = "PPT/PPTX 的浏览器预览兼容性取决于本机环境，若无法显示请直接下载。";
      } else {
        previewHint.value = "";
      }

      previewVisible.value = true;
    };

    const switchNav = (tab) => {
      activeNav.value = tab;
      if (tab === "search") searchReports();
      if (tab === "logs") loadLogs();
    };

    const addPaperRow = () => uploadForm.papers.push({ title: "", file: null });

    const removePaperRow = (index) => {
      if (uploadForm.papers.length === 1) {
        ElMessage.warning("至少保留一行论文信息");
        return;
      }
      uploadForm.papers.splice(index, 1);
    };

    const onPdfChange = (index, file) => {
      uploadForm.papers[index].file = file.raw || null;
    };

    const onPptChange = (file) => {
      uploadForm.pptFile = file.raw || null;
    };

    const resetUploadForm = () => {
      uploadForm.studentName = "";
      uploadForm.reportDate = new Date();
      uploadForm.papers = [{ title: "", file: null }];
      uploadForm.pptFile = null;
    };

    const buildUploadFormData = (confirmDuplicate = false) => {
      const formData = new FormData();
      formData.append("student_name", safeTrim(uploadForm.studentName));
      formData.append("report_date", normalizeDate(uploadForm.reportDate));
      formData.append("confirm_duplicate", String(confirmDuplicate));

      uploadForm.papers.forEach((paper) => {
        formData.append("paper_titles", safeTrim(paper.title));
        formData.append("paper_pdfs", paper.file);
      });

      if (uploadForm.pptFile) {
        formData.append("ppt_file", uploadForm.pptFile);
      }

      return formData;
    };

    const validateUpload = () => {
      if (!safeTrim(uploadForm.studentName)) return "请填写汇报人";
      if (!uploadForm.reportDate) return "请选择汇报日期";

      for (let i = 0; i < uploadForm.papers.length; i += 1) {
        const row = uploadForm.papers[i];
        if (!safeTrim(row.title)) return `第 ${i + 1} 行论文标题为空`;
        if (!row.file) return `第 ${i + 1} 行请上传PDF`;
      }
      return "";
    };

    const doUpload = async (confirmDuplicate = false) => {
      uploadLoading.value = true;
      try {
        const resp = await fetch("/api/reports", {
          method: "POST",
          body: buildUploadFormData(confirmDuplicate),
        });
        const data = await resp.json();

        if (!resp.ok) {
          throw new Error(data.detail || "上传失败");
        }

        if (data.need_confirm && !confirmDuplicate) {
          await ElMessageBox.confirm(data.warning, "重复提醒", {
            type: "warning",
            confirmButtonText: "确认重复上传",
            cancelButtonText: "取消",
          });
          return await doUpload(true);
        }

        ElMessage.success(`上传成功，记录ID: ${data.report_id}`);
        resetUploadForm();
        await loadStudents();
        await searchReports();
        activeNav.value = "search";
      } finally {
        uploadLoading.value = false;
      }
    };

    const submitUpload = async () => {
      const error = validateUpload();
      if (error) {
        ElMessage.error(error);
        return;
      }

      try {
        await doUpload(false);
      } catch (err) {
        const msg = String(err || "");
        if (msg.includes("cancel") || msg.includes("close")) return;
        ElMessage.error(err.message || "上传失败");
      }
    };

    const searchReports = async () => {
      searchLoading.value = true;
      try {
        const params = new URLSearchParams();
        const keyword = safeTrim(searchForm.keyword);
        const studentName = safeTrim(searchForm.studentName);
        if (keyword) params.append("keyword", keyword);
        if (studentName) params.append("student_name", studentName);

        if (searchForm.dateRange?.length === 2) {
          params.append("start_date", normalizeDate(searchForm.dateRange[0]));
          params.append("end_date", normalizeDate(searchForm.dateRange[1]));
        }

        const resp = await fetch(`/api/reports?${params.toString()}`);
        const data = await resp.json();
        if (!resp.ok) {
          throw new Error(data.detail || "检索失败");
        }

        reports.value = data;
      } catch (err) {
        ElMessage.error(err.message || "检索失败");
      } finally {
        searchLoading.value = false;
      }
    };

    const loadLogs = async () => {
      logsLoading.value = true;
      try {
        const params = new URLSearchParams();
        const ip = safeTrim(logForm.ip);
        const keyword = safeTrim(logForm.keyword);

        if (ip) params.append("ip", ip);
        if (keyword) params.append("keyword", keyword);

        if (logForm.dateRange?.length === 2) {
          params.append("start_date", normalizeDate(logForm.dateRange[0]));
          params.append("end_date", normalizeDate(logForm.dateRange[1]));
        }

        const resp = await fetch(`/api/logs?${params.toString()}`);
        const data = await resp.json();
        if (!resp.ok) {
          throw new Error(data.detail || "日志查询失败");
        }

        logs.value = data;
      } catch (err) {
        ElMessage.error(err.message || "日志查询失败");
      } finally {
        logsLoading.value = false;
      }
    };

    const confirmDeleteReport = async (report) => {
      try {
        await ElMessageBox.confirm(
          `确认删除 ${report.student_name} 在 ${report.report_date} 的整条汇报记录吗？该记录下的论文、PDF、PPT都会删除。`,
          "删除整条记录",
          {
            type: "warning",
            confirmButtonText: "确认删除",
            cancelButtonText: "取消",
          }
        );

        const resp = await fetch(`/api/reports/${report.id}`, { method: "DELETE" });
        const data = await resp.json();
        if (!resp.ok) {
          throw new Error(data.detail || "删除失败");
        }

        ElMessage.success("已删除整条记录");
        await loadStudents();
        await searchReports();
      } catch (err) {
        const msg = String(err || "");
        if (msg.includes("cancel") || msg.includes("close")) return;
        ElMessage.error(err.message || "删除失败");
      }
    };

    const loadStudents = async () => {
      const resp = await fetch("/api/students");
      if (!resp.ok) return;
      const data = await resp.json();
      students.value = (data.students || []).map(s => s.name || s);
    };

    const cleanupStaleFiles = async () => {
      cleanupLoading.value = true;
      try {
        const resp = await fetch("/api/admin/cleanup-stale-files", { method: "POST" });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "清理失败");
        ElMessage.success(`清理完成，移除了 ${data.removed} 条孤立文件记录`);
      } catch (err) {
        ElMessage.error(err.message || "清理失败");
      } finally {
        cleanupLoading.value = false;
      }
    };

    const paperPdfFile = (report, paperId) =>
      report.files.find((f) => f.file_type === "pdf" && f.paper_id === paperId);
    const reportPptFile = (report) => report.files.find((f) => f.file_type === "ppt");

    onMounted(async () => {
      await loadStudents();
      await searchReports();
    });

    // SVG icon helpers (consistent stroke-width 2, 24x24 viewBox)
    const icons = {
      upload: '<path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>',
      search: '<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
      log: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
      user: '<circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 10-16 0"/>',
      calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>',
      folder: '<path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/>',
      trash: '<polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>',
      file: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/>',
      download: '<path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>',
      eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
      clean: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6"/><path d="M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/>',
    };

    const iconSvg = (name, size = 15) =>
      `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;

    return {
      uploadLoading,
      searchLoading,
      logsLoading,
      cleanupLoading,
      students,
      reports,
      logs,
      uploadForm,
      searchForm,
      logForm,
      activeNav,
      previewVisible,
      previewUrl,
      previewTitle,
      previewHint,
      openPreview,
      switchNav,
      addPaperRow,
      removePaperRow,
      onPdfChange,
      onPptChange,
      submitUpload,
      searchReports,
      loadLogs,
      confirmDeleteReport,
      cleanupStaleFiles,
      paperPdfFile,
      reportPptFile,
      iconSvg,
    };
  },
  template: `
    <div class="layout">
      <aside class="sidebar">
        <div class="brand">
          <h2>文献管理系统</h2>
          <p>实验室内部使用</p>
        </div>
        <div class="nav">
          <button :class="{ active: activeNav === 'upload' }" @click="switchNav('upload')">
            <span class="icon" v-html="iconSvg('upload')"></span>
            <span class="label">文献上传</span>
          </button>
          <button :class="{ active: activeNav === 'search' }" @click="switchNav('search')">
            <span class="icon" v-html="iconSvg('search')"></span>
            <span class="label">检索浏览</span>
          </button>
          <button :class="{ active: activeNav === 'logs' }" @click="switchNav('logs')">
            <span class="icon" v-html="iconSvg('log')"></span>
            <span class="label">访问日志</span>
          </button>
        </div>
        <div class="sidebar-footer">
          实验室内部使用<br/>文献汇报管理平台
        </div>
      </aside>

      <main class="content">
        <header class="hero">
          <h1>实验室文献汇报平台</h1>
          <p>支持一次提交完整信息、重复检测拦截、按人/按标题/按日期检索。</p>
        </header>

        <!-- Upload Section -->
        <section v-if="activeNav === 'upload'" class="panel">
          <div class="section-header">
            <h3>提交汇报文献</h3>
            <p>填写汇报人信息，上传本次汇报的论文 PDF 及 PPT 附件</p>
          </div>
          <el-form label-width="92px" @submit.prevent>
            <el-row :gutter="12">
              <el-col :xs="24" :md="8">
                <el-form-item label="汇报人">
                  <el-select v-model="uploadForm.studentName" filterable allow-create placeholder="选择或输入学生姓名" style="width:100%">
                    <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="汇报日期">
                  <el-date-picker v-model="uploadForm.reportDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="汇报附件">
                  <el-upload :auto-upload="false" :limit="1" accept=".ppt,.pptx,.pdf" :on-change="onPptChange" :on-remove="() => uploadForm.pptFile = null">
                    <el-button type="primary" plain>上传PPT</el-button>
                  </el-upload>
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider>论文列表</el-divider>

            <div v-for="(paper, index) in uploadForm.papers" :key="index" class="paper-row">
              <el-input v-model="paper.title" placeholder="论文标题（中英文均可）" />
              <el-upload :auto-upload="false" :limit="1" accept=".pdf" :on-change="(file) => onPdfChange(index, file)" :on-remove="() => paper.file = null">
                <el-button plain>上传 PDF</el-button>
              </el-upload>
              <el-button type="danger" plain @click="removePaperRow(index)">
                <span v-html="iconSvg('trash', 14)" style="margin-right:4px"></span>删除
              </el-button>
            </div>

            <el-space>
              <el-button @click="addPaperRow">新增论文行</el-button>
              <el-button type="primary" :loading="uploadLoading" @click="submitUpload">提交上传</el-button>
            </el-space>
          </el-form>
        </section>

        <!-- Search Section -->
        <section v-if="activeNav === 'search'" class="panel">
          <div class="section-header">
            <h3>检索汇报记录</h3>
            <p>按关键词、汇报人或日期范围查找已提交的文献记录</p>
          </div>
          <el-form inline @submit.prevent>
            <el-form-item label="关键词">
              <el-input v-model="searchForm.keyword" placeholder="论文标题模糊搜索" clearable />
            </el-form-item>
            <el-form-item label="汇报人">
              <el-select v-model="searchForm.studentName" filterable clearable placeholder="按人搜索" style="width:220px">
                <el-option v-for="name in students" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="searchLoading" @click="searchReports">检索</el-button>
            </el-form-item>
          </el-form>

          <el-empty v-if="!reports.length" description="暂无数据" />

          <el-card v-for="report in reports" :key="report.id" style="margin-bottom:14px">
            <template #header>
              <div class="card-head">
                <div class="meta-line">
                  <span><span v-html="iconSvg('user', 12)"></span><strong>{{ report.student_name }}</strong></span>
                  <span><span v-html="iconSvg('calendar', 12)"></span>{{ report.report_date }}</span>
                  <span><span v-html="iconSvg('folder', 12)"></span>{{ report.folder_name }}</span>
                </div>
                <el-button type="danger" plain @click="confirmDeleteReport(report)">
                  <span v-html="iconSvg('trash', 14)" style="margin-right:4px"></span>删除本次记录
                </el-button>
              </div>
            </template>

            <el-table :data="report.papers" size="small" style="width:100%">
              <el-table-column label="论文标题" prop="title_raw" />
              <el-table-column label="状态" width="120">
                <template #default="scope">
                  <el-tag :type="scope.row.duplicate_status === 'duplicate' ? 'warning' : 'success'" size="small">
                    {{ scope.row.duplicate_status === 'duplicate' ? '疑似重复' : '唯一' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="PDF" width="220">
                <template #default="scope">
                  <div class="preview-actions">
                    <template v-if="paperPdfFile(report, scope.row.id)">
                      <a :href="'/api/files/' + paperPdfFile(report, scope.row.id).id + '/download'" v-html="iconSvg('download', 14) + ' 下载'"></a>
                      <el-button link class="preview-link-btn" @click="openPreview(paperPdfFile(report, scope.row.id))">
                        <span v-html="iconSvg('eye', 14)" style="margin-right:3px"></span>预览
                      </el-button>
                    </template>
                    <span v-else style="color:#999">-</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="file-links">
              <template v-if="reportPptFile(report)">
                <a :href="'/api/files/' + reportPptFile(report).id + '/download'" v-html="iconSvg('download', 14) + ' 下载 PPT'"></a>
              </template>
              <span v-else style="font-size:13px;color:#999">无 PPT</span>
            </div>
          </el-card>
        </section>

        <!-- Logs Section -->
        <section v-if="activeNav === 'logs'" class="panel">
          <div class="section-header">
            <h3>访问日志</h3>
            <p>记录文件下载、预览等操作的完整访问历史</p>
          </div>
          <el-form inline @submit.prevent>
            <el-form-item label="IP">
              <el-input v-model="logForm.ip" placeholder="按IP过滤" clearable style="width:170px" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="logForm.keyword" placeholder="文件名/论文名/学生" clearable style="width:230px" />
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="logForm.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="logsLoading" @click="loadLogs">查询日志</el-button>
            </el-form-item>
            <el-form-item>
              <el-button :loading="cleanupLoading" @click="cleanupStaleFiles" plain>
                <span v-html="iconSvg('clean', 14)" style="margin-right:4px"></span>清理孤立文件
              </el-button>
            </el-form-item>
          </el-form>

          <el-table :data="logs" size="small" style="width:100%" v-loading="logsLoading">
            <el-table-column label="时间" prop="accessed_at" width="180" />
            <el-table-column label="IP" prop="ip_address" width="150" />
            <el-table-column label="访问动作" prop="action" width="100" />
            <el-table-column label="学生" prop="report_student_name" width="110" />
            <el-table-column label="汇报日期" prop="report_date" width="120" />
            <el-table-column label="论文标题" prop="paper_title" min-width="220" />
            <el-table-column label="文件名" prop="file_name" min-width="220" />
            <el-table-column label="类型" prop="file_type" width="80" />
          </el-table>
        </section>
      </main>

      <el-dialog v-model="previewVisible" :title="'预览：' + previewTitle" width="80%" top="4vh">
        <div v-if="previewHint" class="preview-tip">{{ previewHint }}</div>
        <iframe v-if="previewUrl" :src="previewUrl" class="preview-frame"></iframe>
      </el-dialog>
    </div>
  `,
}).use(ElementPlus).mount("#app");
