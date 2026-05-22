const { createApp, reactive, ref, onMounted } = Vue;
const { ElMessage, ElMessageBox } = ElementPlus;

createApp({
  setup() {
    const uploadLoading = ref(false);
    const searchLoading = ref(false);
    const logsLoading = ref(false);
    const cleanupLoading = ref(false);
    const syncLoading = ref(false);
    const students = ref([]);
    const reports = ref([]);
    const logs = ref([]);
    const activeNav = ref("upload");

    const members = ref([]);
    const membersLoading = ref(false);
    const memberDialogVisible = ref(false);
    const memberDialogMode = ref("add");
    const memberForm = reactive({ id: null, name: "", email: "" });

    const smtpConfig = reactive({ host: "", port: 465, username: "", password: "", sender_name: "", use_tls: true });
    const smtpLoading = ref(false);
    const smtpPasswordTouched = ref(false);

    const allFiles = ref([]);
    const composeForm = reactive({ selectedMembers: [], selectedFiles: [], subject: "", body: "" });
    const composeLoading = ref(false);
    const composeSelectAllMembers = ref(false);
    const composeSelectAllFiles = ref(false);

    const composeTemplate = reactive({
      reportIds: [],
      meetingNumber: "",
      meetingFormat: "线下",
      location: "",
      extra: "",
      nextSpeakers: [],
    });

    const schedules = ref([]);
    const scheduleLoading = ref(false);
    const scheduleDialogVisible = ref(false);
    const scheduleDialogMode = ref("add");
    const scheduleForm = reactive({
      id: null, meeting_date: "", student_name: "", topic: "",
      meeting_format: "线下", location: "", notes: "",
    });

    const paperPool = ref([]);
    const paperPoolLoading = ref(false);
    const paperPoolTab = ref("available");
    const paperPoolDialogVisible = ref(false);
    const paperPoolDialogMode = ref("add");
    const paperPoolForm = reactive({
      id: null, title: "", url: "", recommended_by: "", notes: "",
    });
    const claimDialogVisible = ref(false);
    const claimPaperId = ref(null);
    const claimName = ref("");

    const labFiles = ref([]);
    const labFilesLoading = ref(false);
    const labFilesSearch = reactive({ keyword: "", tag: "" });
    const allTags = ref([]);
    const labFileDialogVisible = ref(false);
    const labFileDialogMode = ref("add");
    const labFileForm = reactive({
      id: null, title: "", description: "", tags: "", uploaded_by: "", file: null,
    });

    const dashboardStats = reactive({ total_reports: 0, total_papers: 0, total_members: 0, monthly_reports: 0 });
    const dashboardByStudent = ref([]);
    const dashboardMonthly = ref([]);
    const dashboardLoading = ref(false);

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
      if (tab === "schedule") loadSchedules();
      if (tab === "pool") loadPaperPool();
      if (tab === "labfiles") { loadLabFiles(); loadLabFileTags(); }
      if (tab === "logs") loadLogs();
      if (tab === "dashboard") loadDashboard();
      if (tab === "members") loadMembers();
      if (tab === "email") loadSmtpConfig();
      if (tab === "compose") { loadMembers(); loadAllFiles(); searchReports(); loadStudents(); }
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

    const syncUploads = async () => {
      syncLoading.value = true;
      try {
        const resp = await fetch("/api/admin/sync-uploads", { method: "POST" });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "同步失败");
        ElMessage.success(`同步完成：导入 ${data.imported} 条，跳过 ${data.skipped} 条`);
        await searchReports();
        await loadStudents();
      } catch (err) {
        ElMessage.error(err.message || "同步失败");
      } finally {
        syncLoading.value = false;
      }
    };

    // ---- 组会排期 ----

    const loadSchedules = async () => {
      scheduleLoading.value = true;
      try {
        const resp = await fetch("/api/schedules");
        if (!resp.ok) throw new Error("加载失败");
        const data = await resp.json();
        schedules.value = data.schedules || [];
      } catch (err) {
        ElMessage.error(err.message || "加载排期失败");
      } finally {
        scheduleLoading.value = false;
      }
    };

    const openAddSchedule = () => {
      scheduleDialogMode.value = "add";
      scheduleForm.id = null;
      scheduleForm.meeting_date = "";
      scheduleForm.student_name = "";
      scheduleForm.topic = "";
      scheduleForm.meeting_format = "线下";
      scheduleForm.location = "";
      scheduleForm.notes = "";
      scheduleDialogVisible.value = true;
    };

    const openEditSchedule = (item) => {
      scheduleDialogMode.value = "edit";
      scheduleForm.id = item.id;
      scheduleForm.meeting_date = item.meeting_date;
      scheduleForm.student_name = item.student_name;
      scheduleForm.topic = item.topic || "";
      scheduleForm.meeting_format = item.meeting_format;
      scheduleForm.location = item.location || "";
      scheduleForm.notes = item.notes || "";
      scheduleDialogVisible.value = true;
    };

    const saveSchedule = async () => {
      if (!safeTrim(scheduleForm.student_name)) return ElMessage.error("请填写汇报人");
      if (!scheduleForm.meeting_date) return ElMessage.error("请选择日期");
      const formData = new FormData();
      formData.append("meeting_date", scheduleForm.meeting_date);
      formData.append("student_name", safeTrim(scheduleForm.student_name));
      formData.append("topic", safeTrim(scheduleForm.topic));
      formData.append("meeting_format", scheduleForm.meeting_format);
      formData.append("location", safeTrim(scheduleForm.location));
      formData.append("notes", safeTrim(scheduleForm.notes));
      try {
        const url = scheduleDialogMode.value === "edit" ? `/api/schedules/${scheduleForm.id}` : "/api/schedules";
        const method = scheduleDialogMode.value === "edit" ? "PUT" : "POST";
        const resp = await fetch(url, { method, body: formData });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "保存失败");
        ElMessage.success(scheduleDialogMode.value === "edit" ? "已更新" : "已创建");
        scheduleDialogVisible.value = false;
        await loadSchedules();
      } catch (err) {
        ElMessage.error(err.message || "保存失败");
      }
    };

    const deleteSchedule = async (item) => {
      try {
        await ElMessageBox.confirm(`确认删除 ${item.student_name} 在 ${item.meeting_date} 的排期？`, "删除排期", { type: "warning" });
        const resp = await fetch(`/api/schedules/${item.id}`, { method: "DELETE" });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "删除失败"); }
        ElMessage.success("已删除");
        await loadSchedules();
      } catch (err) {
        if (String(err).includes("cancel") || String(err).includes("close")) return;
        ElMessage.error(err.message || "删除失败");
      }
    };

    const updateScheduleStatus = async (item, status) => {
      try {
        const formData = new FormData();
        formData.append("status", status);
        const resp = await fetch(`/api/schedules/${item.id}/status`, { method: "PUT", body: formData });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "操作失败"); }
        ElMessage.success("状态已更新");
        await loadSchedules();
      } catch (err) {
        ElMessage.error(err.message || "操作失败");
      }
    };

    const scheduleStatusLabel = (status) => {
      if (status === "upcoming") return "待进行";
      if (status === "completed") return "已完成";
      if (status === "cancelled") return "已取消";
      return status;
    };

    const scheduleStatusType = (status) => {
      if (status === "upcoming") return "primary";
      if (status === "completed") return "success";
      if (status === "cancelled") return "info";
      return "";
    };

    // ---- 论文推荐池 ----

    const loadPaperPool = async (tab) => {
      if (tab) paperPoolTab.value = tab;
      paperPoolLoading.value = true;
      try {
        const resp = await fetch(`/api/paper-pool?status=${paperPoolTab.value}`);
        if (!resp.ok) throw new Error("加载失败");
        const data = await resp.json();
        paperPool.value = data.papers || [];
      } catch (err) {
        ElMessage.error(err.message || "加载论文池失败");
      } finally {
        paperPoolLoading.value = false;
      }
    };

    const openAddPaperPool = () => {
      paperPoolDialogMode.value = "add";
      paperPoolForm.id = null;
      paperPoolForm.title = "";
      paperPoolForm.url = "";
      paperPoolForm.recommended_by = "";
      paperPoolForm.notes = "";
      paperPoolDialogVisible.value = true;
    };

    const openEditPaperPool = (item) => {
      paperPoolDialogMode.value = "edit";
      paperPoolForm.id = item.id;
      paperPoolForm.title = item.title;
      paperPoolForm.url = item.url || "";
      paperPoolForm.recommended_by = item.recommended_by;
      paperPoolForm.notes = item.notes || "";
      paperPoolDialogVisible.value = true;
    };

    const savePaperPool = async () => {
      if (!safeTrim(paperPoolForm.title)) return ElMessage.error("请填写论文标题");
      if (!safeTrim(paperPoolForm.recommended_by)) return ElMessage.error("请填写推荐人");
      const formData = new FormData();
      formData.append("title", safeTrim(paperPoolForm.title));
      formData.append("url", safeTrim(paperPoolForm.url));
      formData.append("recommended_by", safeTrim(paperPoolForm.recommended_by));
      formData.append("notes", safeTrim(paperPoolForm.notes));
      try {
        const url = paperPoolDialogMode.value === "edit" ? `/api/paper-pool/${paperPoolForm.id}` : "/api/paper-pool";
        const method = paperPoolDialogMode.value === "edit" ? "PUT" : "POST";
        const resp = await fetch(url, { method, body: formData });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "保存失败");
        ElMessage.success(paperPoolDialogMode.value === "edit" ? "已更新" : "已推荐");
        paperPoolDialogVisible.value = false;
        await loadPaperPool();
      } catch (err) {
        ElMessage.error(err.message || "保存失败");
      }
    };

    const deletePaperPool = async (item) => {
      try {
        await ElMessageBox.confirm(`确认删除论文「${item.title}」？`, "删除论文", { type: "warning" });
        const resp = await fetch(`/api/paper-pool/${item.id}`, { method: "DELETE" });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "删除失败"); }
        ElMessage.success("已删除");
        await loadPaperPool();
      } catch (err) {
        if (String(err).includes("cancel") || String(err).includes("close")) return;
        ElMessage.error(err.message || "删除失败");
      }
    };

    const openClaimDialog = (paperId) => {
      claimPaperId.value = paperId;
      claimName.value = "";
      claimDialogVisible.value = true;
    };

    const confirmClaim = async () => {
      if (!safeTrim(claimName.value)) return ElMessage.error("请填写你的姓名");
      try {
        const formData = new FormData();
        formData.append("claimed_by", safeTrim(claimName.value));
        const resp = await fetch(`/api/paper-pool/${claimPaperId.value}/claim`, { method: "PUT", body: formData });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "认领失败"); }
        ElMessage.success("认领成功");
        claimDialogVisible.value = false;
        await loadPaperPool();
      } catch (err) {
        ElMessage.error(err.message || "认领失败");
      }
    };

    const unclaimPaper = async (item) => {
      try {
        const resp = await fetch(`/api/paper-pool/${item.id}/unclaim`, { method: "PUT" });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "取消失败"); }
        ElMessage.success("已取消认领");
        await loadPaperPool();
      } catch (err) {
        ElMessage.error(err.message || "取消失败");
      }
    };

    const poolStatusLabel = (status) => {
      if (status === "available") return "待认领";
      if (status === "claimed") return "已认领";
      if (status === "presented") return "已汇报";
      return status;
    };

    const poolStatusType = (status) => {
      if (status === "available") return "warning";
      if (status === "claimed") return "primary";
      if (status === "presented") return "success";
      return "";
    };

    // ---- 数据看板 ----

    const loadDashboard = async () => {
      dashboardLoading.value = true;
      try {
        const [statsResp, studentResp, monthlyResp] = await Promise.all([
          fetch("/api/dashboard/stats"),
          fetch("/api/dashboard/by-student"),
          fetch("/api/dashboard/monthly"),
        ]);
        if (statsResp.ok) {
          const s = await statsResp.json();
          Object.assign(dashboardStats, s);
        }
        if (studentResp.ok) dashboardByStudent.value = await studentResp.json();
        if (monthlyResp.ok) dashboardMonthly.value = await monthlyResp.json();
      } catch (err) {
        ElMessage.error("加载看板数据失败");
      } finally {
        dashboardLoading.value = false;
      }
    };

    const maxStudentReportCount = () => {
      return Math.max(1, ...dashboardByStudent.value.map(s => s.report_count));
    };

    const maxMonthlyReportCount = () => {
      return Math.max(1, ...dashboardMonthly.value.map(m => m.report_count));
    };

    const maxMonthlyPaperCount = () => {
      return Math.max(1, ...dashboardMonthly.value.map(m => m.paper_count));
    };

    // ---- 实验室文件管理 ----

    const loadLabFiles = async () => {
      labFilesLoading.value = true;
      try {
        const params = new URLSearchParams();
        const kw = safeTrim(labFilesSearch.keyword);
        const tg = safeTrim(labFilesSearch.tag);
        if (kw) params.append("keyword", kw);
        if (tg) params.append("tag", tg);
        const resp = await fetch(`/api/lab-files?${params.toString()}`);
        if (!resp.ok) throw new Error("加载失败");
        const data = await resp.json();
        labFiles.value = data.files || [];
      } catch (err) {
        ElMessage.error(err.message || "加载文件失败");
      } finally {
        labFilesLoading.value = false;
      }
    };

    const loadLabFileTags = async () => {
      try {
        const resp = await fetch("/api/lab-files/tags");
        if (resp.ok) allTags.value = await resp.json();
      } catch {}
    };

    const openAddLabFile = () => {
      labFileDialogMode.value = "add";
      labFileForm.id = null;
      labFileForm.title = "";
      labFileForm.description = "";
      labFileForm.tags = "";
      labFileForm.uploaded_by = "";
      labFileForm.file = null;
      labFileDialogVisible.value = true;
    };

    const openEditLabFile = (item) => {
      labFileDialogMode.value = "edit";
      labFileForm.id = item.id;
      labFileForm.title = item.title;
      labFileForm.description = item.description || "";
      labFileForm.tags = item.tags || "";
      labFileForm.uploaded_by = item.uploaded_by || "";
      labFileForm.file = null;
      labFileDialogVisible.value = true;
    };

    const onLabFileChange = (file) => {
      labFileForm.file = file.raw || null;
    };

    const saveLabFile = async () => {
      if (!safeTrim(labFileForm.title)) return ElMessage.error("请填写文件标题");
      if (labFileDialogMode.value === "add" && !labFileForm.file) return ElMessage.error("请选择文件");
      const formData = new FormData();
      formData.append("title", safeTrim(labFileForm.title));
      formData.append("description", safeTrim(labFileForm.description));
      formData.append("tags", safeTrim(labFileForm.tags));
      formData.append("uploaded_by", safeTrim(labFileForm.uploaded_by));
      if (labFileForm.file) formData.append("file", labFileForm.file);
      try {
        const url = labFileDialogMode.value === "edit" ? `/api/lab-files/${labFileForm.id}` : "/api/lab-files";
        const method = labFileDialogMode.value === "edit" ? "PUT" : "POST";
        const resp = await fetch(url, { method, body: formData });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "保存失败");
        ElMessage.success(labFileDialogMode.value === "edit" ? "已更新" : "已上传");
        labFileDialogVisible.value = false;
        await loadLabFiles();
        await loadLabFileTags();
      } catch (err) {
        ElMessage.error(err.message || "保存失败");
      }
    };

    const deleteLabFile = async (item) => {
      try {
        await ElMessageBox.confirm(`确认删除文件「${item.title}」？`, "删除文件", { type: "warning" });
        const resp = await fetch(`/api/lab-files/${item.id}`, { method: "DELETE" });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "删除失败"); }
        ElMessage.success("已删除");
        await loadLabFiles();
      } catch (err) {
        if (String(err).includes("cancel") || String(err).includes("close")) return;
        ElMessage.error(err.message || "删除失败");
      }
    };

    const tagsList = (tagsStr) => {
      if (!tagsStr) return [];
      return tagsStr.split(",").map(t => t.trim()).filter(Boolean);
    };

    // ---- 成员管理 ----

    const loadMembers = async () => {
      membersLoading.value = true;
      try {
        const resp = await fetch("/api/members");
        if (!resp.ok) throw new Error("加载失败");
        const data = await resp.json();
        members.value = data.members || [];
      } catch (err) {
        ElMessage.error(err.message || "加载成员失败");
      } finally {
        membersLoading.value = false;
      }
    };

    const openAddMember = () => {
      memberDialogMode.value = "add";
      memberForm.id = null;
      memberForm.name = "";
      memberForm.email = "";
      memberDialogVisible.value = true;
    };

    const openEditMember = (member) => {
      memberDialogMode.value = "edit";
      memberForm.id = member.id;
      memberForm.name = member.name;
      memberForm.email = member.email;
      memberDialogVisible.value = true;
    };

    const saveMember = async () => {
      if (!safeTrim(memberForm.name)) return ElMessage.error("请填写姓名");
      if (!safeTrim(memberForm.email)) return ElMessage.error("请填写邮箱");
      const formData = new FormData();
      formData.append("name", safeTrim(memberForm.name));
      formData.append("email", safeTrim(memberForm.email));
      try {
        const url = memberDialogMode.value === "edit" ? `/api/members/${memberForm.id}` : "/api/members";
        const method = memberDialogMode.value === "edit" ? "PUT" : "POST";
        const resp = await fetch(url, { method, body: formData });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "保存失败");
        ElMessage.success(memberDialogMode.value === "edit" ? "已更新" : "已添加");
        memberDialogVisible.value = false;
        await loadMembers();
      } catch (err) {
        ElMessage.error(err.message || "保存失败");
      }
    };

    const deleteMember = async (member) => {
      try {
        await ElMessageBox.confirm(`确认删除成员 ${member.name}（${member.email}）？`, "删除成员", { type: "warning" });
        const resp = await fetch(`/api/members/${member.id}`, { method: "DELETE" });
        if (!resp.ok) { const d = await resp.json(); throw new Error(d.detail || "删除失败"); }
        ElMessage.success("已删除");
        await loadMembers();
      } catch (err) {
        if (String(err).includes("cancel") || String(err).includes("close")) return;
        ElMessage.error(err.message || "删除失败");
      }
    };

    // ---- SMTP 配置 ----

    const loadSmtpConfig = async () => {
      try {
        const resp = await fetch("/api/smtp-config");
        if (!resp.ok) return;
        const data = await resp.json();
        smtpConfig.host = data.host || "";
        smtpConfig.port = data.port || 465;
        smtpConfig.username = data.username || "";
        smtpConfig.password = "";
        smtpConfig.sender_name = data.sender_name || "";
        smtpConfig.use_tls = data.use_tls !== false;
        smtpPasswordTouched.value = false;
      } catch {}
    };

    const saveSmtpConfig = async () => {
      smtpLoading.value = true;
      try {
        const formData = new FormData();
        formData.append("host", safeTrim(smtpConfig.host));
        formData.append("port", String(smtpConfig.port));
        formData.append("username", safeTrim(smtpConfig.username));
        if (smtpPasswordTouched.value) formData.append("password", smtpConfig.password);
        formData.append("sender_name", safeTrim(smtpConfig.sender_name));
        formData.append("use_tls", String(smtpConfig.use_tls));
        const resp = await fetch("/api/smtp-config", { method: "PUT", body: formData });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "保存失败");
        ElMessage.success("SMTP配置已保存");
        smtpPasswordTouched.value = false;
      } catch (err) {
        ElMessage.error(err.message || "保存失败");
      } finally {
        smtpLoading.value = false;
      }
    };

    // ---- 自定义邮件 ----

    const loadAllFiles = async () => {
      try {
        const resp = await fetch("/api/files");
        if (!resp.ok) return;
        allFiles.value = await resp.json();
      } catch {}
    };

    const toggleSelectAllMembers = () => {
      if (composeSelectAllMembers.value) {
        composeForm.selectedMembers = members.value.map(m => m.id);
      } else {
        composeForm.selectedMembers = [];
      }
    };

    const toggleSelectAllFiles = () => {
      if (composeSelectAllFiles.value) {
        composeForm.selectedFiles = allFiles.value.map(f => f.id);
      } else {
        composeForm.selectedFiles = [];
      }
    };

    const onReportSelected = (reportIds) => {
      composeTemplate.reportIds = reportIds || [];
      if (!reportIds || !reportIds.length) return;
      const fileIds = [];
      for (const rid of reportIds) {
        const report = reports.value.find(r => r.id === rid);
        if (report) {
          for (const f of report.files) {
            if (!fileIds.includes(f.id)) fileIds.push(f.id);
          }
        }
      }
      composeForm.selectedFiles = fileIds;
      composeSelectAllFiles.value = false;
    };

    const generateEmailContent = () => {
      const selectedReports = (composeTemplate.reportIds || []).map(id => reports.value.find(r => r.id === id)).filter(Boolean);
      const presenters = selectedReports.length
        ? [...new Set(selectedReports.map(r => r.student_name))].join("、")
        : "主讲人";
      const dateStr = selectedReports.length ? selectedReports[0].report_date : "YYYY-MM-DD";

      const num = composeTemplate.meetingNumber || "X";
      const format = composeTemplate.meetingFormat || "线下";
      const loc = composeTemplate.location || "待定";
      const extra = composeTemplate.extra ? `\n${composeTemplate.extra}\n` : "";

      const nextSpeakers = (composeTemplate.nextSpeakers || []).filter(s => s).join("、");
      const nextLine = nextSpeakers ? `\n    下一次研讨会主讲人为：${nextSpeakers}。\n` : "";

      composeForm.subject = `第${num}次研讨会通知`;
      composeForm.body =
`老师和各位同学，

    大家好！

    第${num}次研讨会于${dateStr}进行，形式：${format}，地点：${loc}，本次研讨会主讲人为：${presenters}。
${extra}
    请各位提前阅读附件中的论文，届时参加讨论。
${nextLine}
    ——实验室文献管理系统`;
    };

    const sendComposeEmail = async () => {
      if (!composeForm.selectedMembers.length) return ElMessage.error("请至少选择一位收件人");
      if (!safeTrim(composeForm.subject)) return ElMessage.error("请填写邮件主题");
      composeLoading.value = true;
      try {
        const resp = await fetch("/api/emails/send", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            member_ids: composeForm.selectedMembers,
            file_ids: composeForm.selectedFiles,
            subject: safeTrim(composeForm.subject),
            body: composeForm.body,
          }),
        });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || "发送失败");
        ElMessage.success(data.message || "发送成功");
        composeForm.selectedMembers = [];
        composeForm.selectedFiles = [];
        composeForm.subject = "";
        composeForm.body = "";
        composeSelectAllMembers.value = false;
        composeSelectAllFiles.value = false;
      } catch (err) {
        ElMessage.error(err.message || "发送失败");
      } finally {
        composeLoading.value = false;
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
      mail: '<path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/>',
      send: '<line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>',
    };

    const iconSvg = (name, size = 15) =>
      `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icons[name] || ''}</svg>`;

    const formatFileSize = (bytes) => {
      if (!bytes || bytes === 0) return '';
      if (bytes < 1024) return bytes + ' B';
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
      return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    };

    return {
      uploadLoading,
      searchLoading,
      logsLoading,
      cleanupLoading,
      syncLoading,
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
      syncUploads,
      schedules,
      scheduleLoading,
      scheduleDialogVisible,
      scheduleDialogMode,
      scheduleForm,
      openAddSchedule,
      openEditSchedule,
      saveSchedule,
      deleteSchedule,
      updateScheduleStatus,
      scheduleStatusLabel,
      scheduleStatusType,
      paperPool,
      paperPoolLoading,
      paperPoolTab,
      paperPoolDialogVisible,
      paperPoolDialogMode,
      paperPoolForm,
      claimDialogVisible,
      claimPaperId,
      claimName,
      openAddPaperPool,
      openEditPaperPool,
      savePaperPool,
      deletePaperPool,
      openClaimDialog,
      confirmClaim,
      unclaimPaper,
      poolStatusLabel,
      poolStatusType,
      loadPaperPool,
      dashboardStats,
      dashboardByStudent,
      dashboardMonthly,
      dashboardLoading,
      loadDashboard,
      maxStudentReportCount,
      maxMonthlyReportCount,
      maxMonthlyPaperCount,
      labFiles,
      labFilesLoading,
      labFilesSearch,
      allTags,
      labFileDialogVisible,
      labFileDialogMode,
      labFileForm,
      openAddLabFile,
      openEditLabFile,
      onLabFileChange,
      saveLabFile,
      deleteLabFile,
      loadLabFiles,
      loadLabFileTags,
      tagsList,
      paperPdfFile,
      reportPptFile,
      iconSvg,
      formatFileSize,
      members,
      membersLoading,
      memberDialogVisible,
      memberDialogMode,
      memberForm,
      openAddMember,
      openEditMember,
      saveMember,
      deleteMember,
      smtpConfig,
      smtpLoading,
      smtpPasswordTouched,
      saveSmtpConfig,
      allFiles,
      composeForm,
      composeLoading,
      composeSelectAllMembers,
      composeSelectAllFiles,
      composeTemplate,
      onReportSelected,
      generateEmailContent,
      toggleSelectAllMembers,
      toggleSelectAllFiles,
      sendComposeEmail,
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
          <div class="nav-group">
            <div class="nav-group-label">文献管理</div>
            <button :class="{ active: activeNav === 'upload' }" @click="switchNav('upload')">
              <span class="icon" v-html="iconSvg('upload')"></span>
              <span class="label">文献上传</span>
            </button>
            <button :class="{ active: activeNav === 'search' }" @click="switchNav('search')">
              <span class="icon" v-html="iconSvg('search')"></span>
              <span class="label">检索浏览</span>
            </button>
            <button :class="{ active: activeNav === 'schedule' }" @click="switchNav('schedule')">
              <span class="icon" v-html="iconSvg('calendar')"></span>
              <span class="label">组会排期</span>
            </button>
            <button :class="{ active: activeNav === 'pool' }" @click="switchNav('pool')">
              <span class="icon" v-html="iconSvg('file')"></span>
              <span class="label">论文池</span>
            </button>
            <button :class="{ active: activeNav === 'labfiles' }" @click="switchNav('labfiles')">
              <span class="icon" v-html="iconSvg('folder')"></span>
              <span class="label">文件管理</span>
            </button>
          </div>
          <div class="nav-group">
            <div class="nav-group-label">邮件中心</div>
            <button :class="{ active: activeNav === 'members' }" @click="switchNav('members')">
              <span class="icon" v-html="iconSvg('user')"></span>
              <span class="label">成员管理</span>
            </button>
            <button :class="{ active: activeNav === 'email' }" @click="switchNav('email')">
              <span class="icon" v-html="iconSvg('mail')"></span>
              <span class="label">邮件设置</span>
            </button>
            <button :class="{ active: activeNav === 'compose' }" @click="switchNav('compose')">
              <span class="icon" v-html="iconSvg('send')"></span>
              <span class="label">发送邮件</span>
            </button>
            <button :class="{ active: activeNav === 'logs' }" @click="switchNav('logs')">
              <span class="icon" v-html="iconSvg('log')"></span>
              <span class="label">访问日志</span>
            </button>
            <button :class="{ active: activeNav === 'dashboard' }" @click="switchNav('dashboard')">
              <span class="icon" v-html="iconSvg('calendar')"></span>
              <span class="label">数据看板</span>
            </button>
          </div>
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

        <!-- Schedule Section -->
        <section v-if="activeNav === 'schedule'" class="panel">
          <div class="section-header">
            <h3>组会排期</h3>
            <p>管理未来组会安排，指定汇报人与主题</p>
          </div>
          <el-button type="primary" @click="openAddSchedule" style="margin-bottom:14px">
            <span v-html="iconSvg('calendar', 14)" style="margin-right:4px"></span>新建排期
          </el-button>

          <el-table :data="schedules" size="small" style="width:100%" v-loading="scheduleLoading">
            <el-table-column label="日期" prop="meeting_date" width="120" />
            <el-table-column label="汇报人" prop="student_name" width="120" />
            <el-table-column label="主题" min-width="200">
              <template #default="scope">
                {{ scope.row.topic || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="形式" width="100" prop="meeting_format" />
            <el-table-column label="地点" width="130">
              <template #default="scope">
                {{ scope.row.location || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scheduleStatusType(scope.row.status)" size="small">
                  {{ scheduleStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260">
              <template #default="scope">
                <el-button link type="primary" @click="openEditSchedule(scope.row)">编辑</el-button>
                <el-button v-if="scope.row.status === 'upcoming'" link type="success" @click="updateScheduleStatus(scope.row, 'completed')">完成</el-button>
                <el-button v-if="scope.row.status === 'upcoming'" link type="warning" @click="updateScheduleStatus(scope.row, 'cancelled')">取消</el-button>
                <el-button v-if="scope.row.status !== 'upcoming'" link type="primary" @click="updateScheduleStatus(scope.row, 'upcoming')">恢复</el-button>
                <el-button link type="danger" @click="deleteSchedule(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="scheduleDialogVisible" :title="scheduleDialogMode === 'edit' ? '编辑排期' : '新建排期'" width="520px">
            <el-form label-width="72px">
              <el-form-item label="日期">
                <el-date-picker v-model="scheduleForm.meeting_date" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="汇报人">
                <el-select v-model="scheduleForm.student_name" filterable allow-create placeholder="选择或输入姓名" style="width:100%">
                  <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="主题">
                <el-input v-model="scheduleForm.topic" placeholder="汇报主题（可选）" />
              </el-form-item>
              <el-form-item label="形式">
                <el-select v-model="scheduleForm.meeting_format" style="width:100%">
                  <el-option label="线下" value="线下" />
                  <el-option label="线上" value="线上" />
                  <el-option label="线下+线上" value="线下+线上" />
                </el-select>
              </el-form-item>
              <el-form-item label="地点">
                <el-input v-model="scheduleForm.location" placeholder="如：网安楼329" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="scheduleForm.notes" type="textarea" :rows="2" placeholder="可选备注" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="scheduleDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="saveSchedule">保存</el-button>
            </template>
          </el-dialog>
        </section>

        <!-- Paper Pool Section -->
        <section v-if="activeNav === 'pool'" class="panel">
          <div class="section-header">
            <h3>论文推荐池</h3>
            <p>推荐好论文，组会前认领汇报主题</p>
          </div>
          <div class="pool-toolbar">
            <el-radio-group v-model="paperPoolTab" @change="loadPaperPool($event)" size="small">
              <el-radio-button value="available">待认领</el-radio-button>
              <el-radio-button value="claimed">已认领</el-radio-button>
              <el-radio-button value="presented">已汇报</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" @click="openAddPaperPool">
              <span v-html="iconSvg('file', 14)" style="margin-right:4px"></span>推荐论文
            </el-button>
          </div>

          <el-table :data="paperPool" size="small" style="width:100%" v-loading="paperPoolLoading">
            <el-table-column label="论文标题" min-width="260">
              <template #default="scope">
                <a v-if="scope.row.url" :href="scope.row.url" target="_blank" class="pool-link">{{ scope.row.title }}</a>
                <span v-else>{{ scope.row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="推荐人" prop="recommended_by" width="110" />
            <el-table-column label="认领人" width="110">
              <template #default="scope">
                {{ scope.row.claimed_by || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="poolStatusType(scope.row.status)" size="small">
                  {{ poolStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="160">
              <template #default="scope">
                {{ scope.row.notes || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240">
              <template #default="scope">
                <el-button v-if="scope.row.status === 'available'" link type="primary" @click="openClaimDialog(scope.row.id)">认领</el-button>
                <el-button v-if="scope.row.status === 'claimed'" link type="warning" @click="unclaimPaper(scope.row)">取消认领</el-button>
                <el-button link type="primary" @click="openEditPaperPool(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="deletePaperPool(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 推荐/编辑弹窗 -->
          <el-dialog v-model="paperPoolDialogVisible" :title="paperPoolDialogMode === 'edit' ? '编辑论文' : '推荐论文'" width="520px">
            <el-form label-width="72px">
              <el-form-item label="论文标题">
                <el-input v-model="paperPoolForm.title" placeholder="论文标题" />
              </el-form-item>
              <el-form-item label="链接">
                <el-input v-model="paperPoolForm.url" placeholder="论文 URL（可选）" />
              </el-form-item>
              <el-form-item label="推荐人">
                <el-select v-model="paperPoolForm.recommended_by" filterable allow-create placeholder="选择或输入姓名" style="width:100%">
                  <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="推荐理由">
                <el-input v-model="paperPoolForm.notes" type="textarea" :rows="3" placeholder="为什么推荐这篇论文？（可选）" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="paperPoolDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="savePaperPool">保存</el-button>
            </template>
          </el-dialog>

          <!-- 认领弹窗 -->
          <el-dialog v-model="claimDialogVisible" title="认领论文" width="380px">
            <el-form label-width="56px">
              <el-form-item label="姓名">
                <el-select v-model="claimName" filterable allow-create placeholder="选择或输入你的姓名" style="width:100%">
                  <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="claimDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="confirmClaim">确认认领</el-button>
            </template>
          </el-dialog>
        </section>

        <!-- Lab Files Section -->
        <section v-if="activeNav === 'labfiles'" class="panel">
          <div class="section-header">
            <h3>文件管理</h3>
            <p>管理实验室内部文件、模板和文档资源</p>
          </div>

          <div class="labfiles-toolbar">
            <el-form inline @submit.prevent>
              <el-form-item>
                <el-input v-model="labFilesSearch.keyword" placeholder="搜索标题/描述" clearable style="width:200px" />
              </el-form-item>
              <el-form-item>
                <el-select v-model="labFilesSearch.tag" placeholder="按标签筛选" clearable filterable style="width:160px" @change="loadLabFiles">
                  <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="labFilesLoading" @click="loadLabFiles">搜索</el-button>
              </el-form-item>
            </el-form>
            <el-button type="primary" @click="openAddLabFile">
              <span v-html="iconSvg('upload', 14)" style="margin-right:4px"></span>上传文件
            </el-button>
          </div>

          <el-empty v-if="!labFiles.length && !labFilesLoading" description="暂无文件" />

          <div class="labfiles-grid">
            <div v-for="f in labFiles" :key="f.id" class="labfile-card">
              <div class="labfile-card-header">
                <div class="labfile-title">{{ f.title }}</div>
                <div class="labfile-meta">
                  <span class="labfile-size">{{ formatFileSize(f.file_size) }}</span>
                  <span v-if="f.uploaded_by" class="labfile-uploader">{{ f.uploaded_by }}</span>
                </div>
              </div>
              <div v-if="f.description" class="labfile-desc">{{ f.description }}</div>
              <div class="labfile-tags">
                <el-tag v-for="t in tagsList(f.tags)" :key="t" size="small" type="info" effect="plain" class="labfile-tag">{{ t }}</el-tag>
              </div>
              <div class="labfile-file">{{ f.original_name }}</div>
              <div class="labfile-actions">
                <a :href="'/api/lab-files/' + f.id + '/download'" class="labfile-action-btn">
                  <span v-html="iconSvg('download', 13)"></span> 下载
                </a>
                <a :href="'/api/lab-files/' + f.id + '/preview'" target="_blank" class="labfile-action-btn">
                  <span v-html="iconSvg('eye', 13)"></span> 预览
                </a>
                <button class="labfile-action-btn" @click="openEditLabFile(f)">
                  <span v-html="iconSvg('file', 13)"></span> 编辑
                </button>
                <button class="labfile-action-btn danger" @click="deleteLabFile(f)">
                  <span v-html="iconSvg('trash', 13)"></span> 删除
                </button>
              </div>
            </div>
          </div>

          <!-- 上传/编辑弹窗 -->
          <el-dialog v-model="labFileDialogVisible" :title="labFileDialogMode === 'edit' ? '编辑文件信息' : '上传文件'" width="520px">
            <el-form label-width="72px">
              <el-form-item label="标题">
                <el-input v-model="labFileForm.title" placeholder="文件标题" />
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="labFileForm.description" type="textarea" :rows="2" placeholder="文件描述（可选）" />
              </el-form-item>
              <el-form-item label="标签">
                <el-input v-model="labFileForm.tags" placeholder="多个标签用逗号分隔，如：模板,Word,开题报告" />
              </el-form-item>
              <el-form-item label="上传人">
                <el-select v-model="labFileForm.uploaded_by" filterable allow-create clearable placeholder="选择或输入姓名" style="width:100%">
                  <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="labFileDialogMode === 'add'" label="选择文件">
                <el-upload :auto-upload="false" :limit="1" :on-change="onLabFileChange" :on-remove="() => labFileForm.file = null">
                  <el-button plain>选择文件</el-button>
                </el-upload>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="labFileDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="saveLabFile">保存</el-button>
            </template>
          </el-dialog>
        </section>

        <!-- Dashboard Section -->
        <section v-if="activeNav === 'dashboard'" class="panel">
          <div class="section-header">
            <h3>数据看板</h3>
            <p>组会数据统计与趋势分析</p>
          </div>

          <div class="dashboard-stats" v-loading="dashboardLoading">
            <div class="stat-card">
              <div class="stat-value">{{ dashboardStats.total_reports }}</div>
              <div class="stat-label">总汇报次数</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ dashboardStats.total_papers }}</div>
              <div class="stat-label">总论文数量</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ dashboardStats.total_members }}</div>
              <div class="stat-label">实验室成员</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ dashboardStats.monthly_reports }}</div>
              <div class="stat-label">本月汇报</div>
            </div>
          </div>

          <el-row :gutter="20">
            <el-col :xs="24" :md="12">
              <div class="section-header" style="margin-top:8px">
                <h3>学生排行榜</h3>
                <p>按汇报次数排序</p>
              </div>
              <div v-if="dashboardByStudent.length" class="bar-chart">
                <div v-for="s in dashboardByStudent" :key="s.student_name" class="bar-col">
                  <div class="bar-value">{{ s.report_count }}</div>
                  <div class="bar-fill primary" :style="{ height: (s.report_count / maxStudentReportCount() * 100) + '%' }"></div>
                  <div class="bar-label">{{ s.student_name }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无数据" :image-size="48" />
            </el-col>
            <el-col :xs="24" :md="12">
              <div class="section-header" style="margin-top:8px">
                <h3>月度趋势</h3>
                <p>近 12 个月汇报与论文数量</p>
              </div>
              <div v-if="dashboardMonthly.length" class="bar-chart">
                <div v-for="m in dashboardMonthly" :key="m.month" class="bar-col">
                  <div class="bar-value">{{ m.report_count }}</div>
                  <div class="bar-fill primary" :style="{ height: (m.report_count / maxMonthlyReportCount() * 100) + '%' }"></div>
                  <div class="bar-label">{{ m.month.slice(5) }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无数据" :image-size="48" />
            </el-col>
          </el-row>

          <div v-if="dashboardByStudent.length" style="margin-top:20px">
            <el-table :data="dashboardByStudent" size="small" style="width:100%">
              <el-table-column label="学生" prop="student_name" />
              <el-table-column label="汇报次数" prop="report_count" width="120" />
              <el-table-column label="论文数量" prop="paper_count" width="120" />
            </el-table>
          </div>
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
              <el-button :loading="syncLoading" @click="syncUploads" type="success" plain>
                <span v-html="iconSvg('folder', 14)" style="margin-right:4px"></span>同步 uploads 目录
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

        <!-- Members Section -->
        <section v-if="activeNav === 'members'" class="panel">
          <div class="section-header">
            <h3>实验室成员管理</h3>
            <p>管理实验室成员邮箱，用于文献汇报邮件通知</p>
          </div>
          <el-button type="primary" @click="openAddMember" style="margin-bottom:14px">
            <span v-html="iconSvg('user', 14)" style="margin-right:4px"></span>添加成员
          </el-button>
          <el-table :data="members" size="small" style="width:100%" v-loading="membersLoading">
            <el-table-column label="姓名" prop="name" width="180" />
            <el-table-column label="邮箱" prop="email" />
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button link type="primary" @click="openEditMember(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="deleteMember(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="memberDialogVisible" :title="memberDialogMode === 'edit' ? '编辑成员' : '添加成员'" width="420px">
            <el-form label-width="56px">
              <el-form-item label="姓名">
                <el-input v-model="memberForm.name" placeholder="成员姓名" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="memberForm.email" placeholder="member@example.com" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="memberDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="saveMember">保存</el-button>
            </template>
          </el-dialog>
        </section>

        <!-- Email Settings Section -->
        <section v-if="activeNav === 'email'" class="panel">
          <div class="section-header">
            <h3>邮件发送设置</h3>
            <p>配置 SMTP 服务器信息，用于发送文献汇报通知邮件</p>
          </div>
          <el-form label-width="110px" style="max-width:560px">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="smtpConfig.host" placeholder="smtp.qq.com" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="smtpConfig.port" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="smtpConfig.username" placeholder="发件人邮箱地址" />
            </el-form-item>
            <el-form-item label="密码/授权码">
              <el-input v-model="smtpConfig.password" type="password" show-password placeholder="留空表示不修改" @focus="smtpPasswordTouched = true" />
            </el-form-item>
            <el-form-item label="发件人名称">
              <el-input v-model="smtpConfig.sender_name" placeholder="实验室文献系统" />
            </el-form-item>
            <el-form-item label="使用 SSL/TLS">
              <el-switch v-model="smtpConfig.use_tls" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="smtpLoading" @click="saveSmtpConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
        </section>

        <!-- Compose Email Section -->
        <section v-if="activeNav === 'compose'" class="panel">
          <div class="section-header">
            <h3>发送邮件</h3>
            <p>关联汇报记录，填写研讨会信息，自动生成邮件内容</p>
          </div>

          <!-- Step 1: Template Fields -->
          <div class="compose-block">
            <div class="compose-block-title"><strong>研讨会信息</strong></div>
            <el-form label-width="90px">
              <el-row :gutter="16">
                <el-col :xs="24" :md="12">
                  <el-form-item label="关联汇报">
                    <el-select v-model="composeTemplate.reportIds" placeholder="可多选汇报记录" clearable multiple collapse-tags collapse-tags-tooltip style="width:100%" @change="onReportSelected">
                      <el-option v-for="r in reports" :key="r.id" :label="r.student_name + ' - ' + r.report_date" :value="r.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="研讨会序号">
                    <el-input v-model="composeTemplate.meetingNumber" placeholder="如：4" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :xs="24" :md="12">
                  <el-form-item label="会议形式">
                    <el-select v-model="composeTemplate.meetingFormat" style="width:100%">
                      <el-option label="线下" value="线下" />
                      <el-option label="线上" value="线上" />
                      <el-option label="线下+线上" value="线下+线上" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="会议地点">
                    <el-input v-model="composeTemplate.location" placeholder="如：网安楼329" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :xs="24" :md="12">
                  <el-form-item label="下次主讲人">
                    <el-select v-model="composeTemplate.nextSpeakers" placeholder="选择下次研讨会主讲人" clearable multiple filterable allow-create collapse-tags collapse-tags-tooltip style="width:100%">
                      <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="补充内容">
                <el-input v-model="composeTemplate.extra" type="textarea" :rows="3" placeholder="可选，如需补充说明请在此填写" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="generateEmailContent">生成邮件内容</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- Step 2: Email Content Preview -->
          <div class="compose-block" style="margin-top:16px">
            <div class="compose-block-title"><strong>邮件内容</strong></div>
            <el-form label-width="56px">
              <el-form-item label="主题">
                <el-input v-model="composeForm.subject" placeholder="点击上方「生成邮件内容」自动填充" />
              </el-form-item>
              <el-form-item label="正文">
                <el-input v-model="composeForm.body" type="textarea" :rows="14" placeholder="点击上方「生成邮件内容」自动填充，可手动修改" />
              </el-form-item>
            </el-form>
          </div>

          <!-- Step 3: Recipients & Attachments -->
          <el-row :gutter="20" style="margin-top:16px">
            <el-col :xs="24" :md="10">
              <div class="compose-block">
                <div class="compose-block-title">
                  <strong>收件人</strong>
                  <el-checkbox v-model="composeSelectAllMembers" @change="toggleSelectAllMembers">全选</el-checkbox>
                </div>
                <div class="compose-list">
                  <el-checkbox-group v-model="composeForm.selectedMembers">
                    <div v-for="m in members" :key="m.id" class="compose-list-item">
                      <el-checkbox :label="m.id">{{ m.name }} ({{ m.email }})</el-checkbox>
                    </div>
                  </el-checkbox-group>
                  <el-empty v-if="!members.length" description="暂无成员，请先添加" :image-size="48" />
                </div>
              </div>
            </el-col>

            <el-col :xs="24" :md="14">
              <div class="compose-block">
                <div class="compose-block-title">
                  <strong>附件</strong>
                  <el-checkbox v-model="composeSelectAllFiles" @change="toggleSelectAllFiles">全选</el-checkbox>
                </div>
                <div class="compose-list">
                  <el-checkbox-group v-model="composeForm.selectedFiles">
                    <div v-for="f in allFiles" :key="f.id" class="compose-list-item">
                      <el-checkbox :label="f.id">
                        <span class="file-type-tag" :class="f.file_type">{{ f.file_type.toUpperCase() }}</span>
                        {{ f.original_name }}
                        <span class="file-size-tag" v-if="f.file_size">{{ formatFileSize(f.file_size) }}</span>
                        <span class="file-source" v-if="f.report_student_name">（{{ f.report_student_name }} {{ f.report_date }}）</span>
                      </el-checkbox>
                    </div>
                  </el-checkbox-group>
                  <el-empty v-if="!allFiles.length" description="暂无文件" :image-size="48" />
                </div>
              </div>
            </el-col>
          </el-row>

          <div style="margin-top:16px;text-align:right">
            <el-button type="primary" size="large" :loading="composeLoading" @click="sendComposeEmail">
              <span v-html="iconSvg('send', 14)" style="margin-right:4px"></span>发送邮件
            </el-button>
          </div>
        </section>
      </main>

      <el-dialog v-model="previewVisible" :title="'预览：' + previewTitle" width="80%" top="4vh">
        <div v-if="previewHint" class="preview-tip">{{ previewHint }}</div>
        <iframe v-if="previewUrl" :src="previewUrl" class="preview-frame"></iframe>
      </el-dialog>
    </div>
  `,
}).use(ElementPlus).mount("#app");
