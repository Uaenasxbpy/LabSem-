<template>
  <div class="view-search">
    <div class="hero">
      <h1>检索浏览</h1>
      <p>按关键词、汇报人或日期范围查找已提交的文献记录</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>检索汇报记录</h3>
        <p>按关键词、汇报人或日期范围查找已提交的文献记录</p>
      </div>

      <el-form inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="论文标题模糊搜索"
            clearable
          />
        </el-form-item>
        <el-form-item label="汇报人">
          <el-select
            v-model="searchForm.studentName"
            filterable
            clearable
            placeholder="按人搜索"
            style="width: 220px"
          >
            <el-option
              v-for="name in students"
              :key="name"
              :label="name"
              :value="name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="searchLoading"
            @click="doSearch"
          >
            检索
          </el-button>
        </el-form-item>
      </el-form>

      <el-empty v-if="!reports.length && !searchLoading" description="暂无数据" />

      <ReportCard
        v-for="report in reports"
        :key="report.id"
        :report="report"
        @delete="confirmDeleteReport"
        @preview="openPreview"
      />
    </div>

    <!-- File preview dialog -->
    <el-dialog
      v-model="previewVisible"
      :title="'预览：' + previewTitle"
      width="80%"
      top="4vh"
    >
      <div v-if="previewHint" class="preview-tip">{{ previewHint }}</div>
      <FilePreview
        v-if="previewFileId"
        :file-id="previewFileId"
        :file-name="previewTitle"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as reportApi from '@/api/report'
import * as studentApi from '@/api/student'
import ReportCard from '@/components/ReportCard.vue'
import FilePreview from '@/components/FilePreview.vue'

defineOptions({ name: 'SearchView' })

const students = ref([])
const reports = ref([])
const searchLoading = ref(false)

const previewVisible = ref(false)
const previewFileId = ref(null)
const previewTitle = ref('')
const previewHint = ref('')

const searchForm = reactive({
  keyword: '',
  studentName: '',
  dateRange: [],
})

const safeTrim = (value) => String(value ?? '').trim()

const normalizeDate = (d) => {
  if (!d) return ''
  if (typeof d === 'string') return d
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const doSearch = async () => {
  searchLoading.value = true
  try {
    const params = {}
    const keyword = safeTrim(searchForm.keyword)
    const studentName = safeTrim(searchForm.studentName)
    if (keyword) params.keyword = keyword
    if (studentName) params.student_name = studentName

    if (searchForm.dateRange?.length === 2) {
      params.start_date = normalizeDate(searchForm.dateRange[0])
      params.end_date = normalizeDate(searchForm.dateRange[1])
    }

    const data = await reportApi.searchReports(params)
    reports.value = data
  } catch (err) {
    ElMessage.error(err.message || '检索失败')
  } finally {
    searchLoading.value = false
  }
}

const openPreview = (report) => {
  // Find the first PDF file in this report to preview
  if (!report || !report.files || !report.files.length) return
  const pdfFile = report.files.find((f) => f.file_type === 'pdf')
  if (!pdfFile) return

  previewTitle.value = pdfFile.original_name || '文件预览'
  previewFileId.value = pdfFile.id
  previewHint.value = ''
  previewVisible.value = true
}

const confirmDeleteReport = async (reportId) => {
  const report = reports.value.find((r) => r.id === reportId)
  if (!report) return

  try {
    await ElMessageBox.confirm(
      `确认删除 ${report.student_name} 在 ${report.report_date} 的整条汇报记录吗？该记录下的论文、PDF、PPT都会删除。`,
      '删除整条记录',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      }
    )

    await reportApi.deleteReport(reportId)
    ElMessage.success('已删除整条记录')
    await loadStudents()
    await doSearch()
  } catch (err) {
    const msg = String(err || '')
    if (msg.includes('cancel') || msg.includes('close')) return
    ElMessage.error(err.message || '删除失败')
  }
}

const loadStudents = async () => {
  try {
    const data = await studentApi.getStudents()
    students.value = (data.students || []).map((s) => s.name || s)
  } catch {
    // silent
  }
}

onMounted(async () => {
  await loadStudents()
  await doSearch()
})
</script>

<style scoped>
.view-search {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
