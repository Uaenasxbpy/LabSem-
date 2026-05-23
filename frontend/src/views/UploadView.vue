<template>
  <div class="view-upload">
    <div class="hero">
      <h1>文献上传</h1>
      <p>上传组会汇报的论文 PDF 和 PPT 文件</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>提交汇报文献</h3>
        <p>填写汇报人信息，上传本次汇报的论文 PDF 及 PPT 附件</p>
      </div>

      <el-form label-width="92px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :xs="24" :md="8">
            <el-form-item label="汇报人">
              <el-select
                v-model="uploadForm.studentName"
                filterable
                allow-create
                placeholder="选择或输入学生姓名"
                style="width: 100%"
              >
                <el-option
                  v-for="s in students"
                  :key="s"
                  :label="s"
                  :value="s"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="汇报日期">
              <el-date-picker
                v-model="uploadForm.reportDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="汇报附件">
              <el-upload
                :auto-upload="false"
                :limit="1"
                accept=".ppt,.pptx,.pdf"
                :on-change="onPptChange"
                :on-remove="() => (uploadForm.pptFile = null)"
              >
                <el-button type="primary" plain>上传PPT</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider>论文列表</el-divider>

        <PaperRow
          v-for="(paper, index) in uploadForm.papers"
          :key="index"
          :index="index"
          :model-value="paper"
          @update:model-value="uploadForm.papers[index] = $event"
          @delete="removePaperRow(index)"
        />

        <el-space>
          <el-button @click="addPaperRow">新增论文行</el-button>
          <el-button
            type="primary"
            :loading="uploadLoading"
            @click="submitUpload"
          >
            提交上传
          </el-button>
        </el-space>
      </el-form>
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
import PaperRow from '@/components/PaperRow.vue'
import FilePreview from '@/components/FilePreview.vue'

defineOptions({ name: 'UploadView' })

const students = ref([])
const uploadLoading = ref(false)

const previewVisible = ref(false)
const previewFileId = ref(null)
const previewTitle = ref('')
const previewHint = ref('')

const uploadForm = reactive({
  studentName: '',
  reportDate: new Date(),
  papers: [{ title: '', file: null }],
  pptFile: null,
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

const addPaperRow = () => uploadForm.papers.push({ title: '', file: null })

const removePaperRow = (index) => {
  if (uploadForm.papers.length === 1) {
    ElMessage.warning('至少保留一行论文信息')
    return
  }
  uploadForm.papers.splice(index, 1)
}

const onPptChange = (file) => {
  uploadForm.pptFile = file.raw || null
}

const resetUploadForm = () => {
  uploadForm.studentName = ''
  uploadForm.reportDate = new Date()
  uploadForm.papers = [{ title: '', file: null }]
  uploadForm.pptFile = null
}

const validateUpload = () => {
  if (!safeTrim(uploadForm.studentName)) return '请填写汇报人'
  if (!uploadForm.reportDate) return '请选择汇报日期'

  for (let i = 0; i < uploadForm.papers.length; i += 1) {
    const row = uploadForm.papers[i]
    if (!safeTrim(row.title)) return `第 ${i + 1} 行论文标题为空`
    if (!row.file) return `第 ${i + 1} 行请上传PDF`
  }
  return ''
}

const buildUploadFormData = (confirmDuplicate = false) => {
  const formData = new FormData()
  formData.append('student_name', safeTrim(uploadForm.studentName))
  formData.append('report_date', normalizeDate(uploadForm.reportDate))
  formData.append('confirm_duplicate', String(confirmDuplicate))

  uploadForm.papers.forEach((paper) => {
    formData.append('paper_titles', safeTrim(paper.title))
    formData.append('paper_pdfs', paper.file)
  })

  if (uploadForm.pptFile) {
    formData.append('ppt_file', uploadForm.pptFile)
  }

  return formData
}

const doUpload = async (confirmDuplicate = false) => {
  uploadLoading.value = true
  try {
    const data = await reportApi.createReport(buildUploadFormData(confirmDuplicate))

    if (data.need_confirm && !confirmDuplicate) {
      await ElMessageBox.confirm(data.warning, '重复提醒', {
        type: 'warning',
        confirmButtonText: '确认重复上传',
        cancelButtonText: '取消',
      })
      return await doUpload(true)
    }

    ElMessage.success(`上传成功，记录ID: ${data.report_id}`)
    resetUploadForm()
    await loadStudents()
  } finally {
    uploadLoading.value = false
  }
}

const submitUpload = async () => {
  const error = validateUpload()
  if (error) {
    ElMessage.error(error)
    return
  }

  try {
    await doUpload(false)
  } catch (err) {
    const msg = String(err || '')
    if (msg.includes('cancel') || msg.includes('close')) return
    ElMessage.error(err.message || '上传失败')
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

onMounted(() => {
  loadStudents()
})
</script>
