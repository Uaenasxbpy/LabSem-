<template>
  <div class="view-compose">
    <div class="hero">
      <h1>发送邮件</h1>
      <p>向实验室成员发送邮件通知</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>发送邮件</h3>
        <p>关联汇报记录，填写研讨会信息，自动生成邮件内容</p>
      </div>

      <!-- Section 1: Meeting info + template generation -->
      <div class="compose-block">
        <div class="compose-block-title"><strong>研讨会信息</strong></div>
        <el-form label-width="90px">
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item label="关联汇报">
                <el-select
                  v-model="templateForm.reportIds"
                  placeholder="可多选汇报记录"
                  clearable
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  style="width: 100%"
                  @change="onReportSelected"
                >
                  <el-option
                    v-for="r in reports"
                    :key="r.id"
                    :label="r.student_name + ' - ' + r.report_date"
                    :value="r.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="研讨会序号">
                <el-input v-model="templateForm.meetingNumber" placeholder="如：4" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item label="会议形式">
                <el-select v-model="templateForm.meetingFormat" style="width: 100%">
                  <el-option label="线下" value="线下" />
                  <el-option label="线上" value="线上" />
                  <el-option label="线下+线上" value="线下+线上" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="会议地点">
                <el-input v-model="templateForm.location" placeholder="如：网安楼329" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item label="下次主讲人">
                <el-select
                  v-model="templateForm.nextSpeakers"
                  placeholder="选择下次研讨会主讲人"
                  clearable
                  multiple
                  filterable
                  allow-create
                  collapse-tags
                  collapse-tags-tooltip
                  style="width: 100%"
                >
                  <el-option v-for="s in students" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="补充内容">
            <el-input
              v-model="templateForm.extra"
              type="textarea"
              :rows="3"
              placeholder="可选，如需补充说明请在此填写"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="generateEmailContent">生成邮件内容</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- Section 2: Email content preview -->
      <div class="compose-block" style="margin-top: 16px">
        <div class="compose-block-title"><strong>邮件内容</strong></div>
        <el-form label-width="56px">
          <el-form-item label="主题">
            <el-input
              v-model="composeForm.subject"
              placeholder="点击上方「生成邮件内容」自动填充"
            />
          </el-form-item>
          <el-form-item label="正文">
            <el-input
              v-model="composeForm.body"
              type="textarea"
              :rows="14"
              placeholder="点击上方「生成邮件内容」自动填充，可手动修改"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- Section 3 & 4: Recipients + Attachments -->
      <el-row :gutter="20" style="margin-top: 16px">
        <el-col :xs="24" :md="10">
          <div class="compose-block">
            <div class="compose-block-title">
              <strong>收件人</strong>
              <el-checkbox v-model="selectAllMembers" @change="toggleSelectAllMembers">
                全选
              </el-checkbox>
            </div>
            <div class="compose-list">
              <el-checkbox-group v-model="composeForm.selectedMembers">
                <div v-for="m in members" :key="m.id" class="compose-list-item">
                  <el-checkbox :value="m.id">{{ m.name }} ({{ m.email }})</el-checkbox>
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
              <el-checkbox v-model="selectAllFiles" @change="toggleSelectAllFiles">
                全选
              </el-checkbox>
            </div>
            <div class="compose-list">
              <el-checkbox-group v-model="composeForm.selectedFiles">
                <div v-for="f in allFiles" :key="f.id" class="compose-list-item">
                  <el-checkbox :value="f.id">
                    <span class="file-type-tag" :class="f.file_type">
                      {{ f.file_type.toUpperCase() }}
                    </span>
                    {{ f.original_name }}
                    <span v-if="f.file_size" class="file-size-tag">
                      {{ formatFileSize(f.file_size) }}
                    </span>
                    <span v-if="f.report_student_name" class="file-source">
                      （{{ f.report_student_name }} {{ f.report_date }}）
                    </span>
                  </el-checkbox>
                </div>
              </el-checkbox-group>
              <el-empty v-if="!allFiles.length" description="暂无文件" :image-size="48" />
            </div>
          </div>
        </el-col>
      </el-row>

      <div style="margin-top: 16px; text-align: right">
        <el-button
          type="primary"
          size="large"
          :loading="composeLoading"
          @click="handleSendEmail"
        >
          <Icon name="send" :size="14" style="margin-right: 4px" />
          发送邮件
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as memberApi from '@/api/member'
import * as emailApi from '@/api/email'
import * as reportApi from '@/api/report'
import * as fileApi from '@/api/file'
import * as studentApi from '@/api/student'
import Icon from '@/components/Icon.vue'

defineOptions({ name: 'ComposeView' })

const members = ref([])
const students = ref([])
const reports = ref([])
const allFiles = ref([])
const composeLoading = ref(false)
const selectAllMembers = ref(false)
const selectAllFiles = ref(false)

const templateForm = reactive({
  reportIds: [],
  meetingNumber: '',
  meetingFormat: '线下',
  location: '',
  extra: '',
  nextSpeakers: []
})

const composeForm = reactive({
  selectedMembers: [],
  selectedFiles: [],
  subject: '',
  body: ''
})

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

async function loadMembers() {
  try {
    const data = await memberApi.getMembers()
    members.value = data.members || []
  } catch {
    // silent
  }
}

async function loadStudents() {
  try {
    const data = await studentApi.getStudents()
    students.value = (data.students || []).map((s) => (typeof s === 'string' ? s : s.name))
  } catch {
    // silent
  }
}

async function loadReports() {
  try {
    const data = await reportApi.searchReports({})
    reports.value = Array.isArray(data) ? data : []
  } catch {
    // silent
  }
}

async function loadAllFiles() {
  try {
    const data = await fileApi.getFiles()
    allFiles.value = Array.isArray(data) ? data : []
  } catch {
    // silent
  }
}

function onReportSelected(reportIds) {
  if (!reportIds || !reportIds.length) return
  const fileIds = []
  for (const rid of reportIds) {
    const report = reports.value.find((r) => r.id === rid)
    if (report && report.files) {
      for (const f of report.files) {
        if (!fileIds.includes(f.id)) fileIds.push(f.id)
      }
    }
  }
  composeForm.selectedFiles = fileIds
  selectAllFiles.value = false
}

function toggleSelectAllMembers() {
  composeForm.selectedMembers = selectAllMembers.value
    ? members.value.map((m) => m.id)
    : []
}

function toggleSelectAllFiles() {
  composeForm.selectedFiles = selectAllFiles.value
    ? allFiles.value.map((f) => f.id)
    : []
}

function generateEmailContent() {
  const selectedReports = (templateForm.reportIds || [])
    .map((id) => reports.value.find((r) => r.id === id))
    .filter(Boolean)

  const presenters = selectedReports.length
    ? [...new Set(selectedReports.map((r) => r.student_name))].join('、')
    : '主讲人'

  const dateStr = selectedReports.length ? selectedReports[0].report_date : 'YYYY-MM-DD'

  const num = templateForm.meetingNumber || 'X'
  const format = templateForm.meetingFormat || '线下'
  const loc = templateForm.location || '待定'
  const extra = templateForm.extra ? `\n${templateForm.extra}\n` : ''

  const nextSpeakers = (templateForm.nextSpeakers || []).filter(Boolean).join('、')
  const nextLine = nextSpeakers
    ? `\n    下一次研讨会主讲人为：${nextSpeakers}。\n`
    : ''

  composeForm.subject = `第${num}次研讨会通知`
  composeForm.body = `老师和各位同学，

    大家好！

    第${num}次研讨会于${dateStr}进行，形式：${format}，地点：${loc}，本次研讨会主讲人为：${presenters}。
${extra}
    请各位提前阅读附件中的论文，届时参加讨论。
${nextLine}
    ——实验室文献管理系统`
}

async function handleSendEmail() {
  if (!composeForm.selectedMembers.length) {
    ElMessage.error('请至少选择一位收件人')
    return
  }
  if (!composeForm.subject.trim()) {
    ElMessage.error('请填写邮件主题')
    return
  }

  composeLoading.value = true
  try {
    await emailApi.sendEmail({
      member_ids: composeForm.selectedMembers,
      file_ids: composeForm.selectedFiles,
      subject: composeForm.subject.trim(),
      body: composeForm.body
    })
    ElMessage.success('发送成功')
    composeForm.selectedMembers = []
    composeForm.selectedFiles = []
    composeForm.subject = ''
    composeForm.body = ''
    selectAllMembers.value = false
    selectAllFiles.value = false
  } catch (err) {
    ElMessage.error(err.message || '发送失败')
  } finally {
    composeLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadMembers(), loadStudents(), loadReports(), loadAllFiles()])
})
</script>

<style scoped>
.compose-block {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}

.compose-block-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 15px;
}

.compose-list {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
}

.compose-list-item {
  padding: 4px 0;
}

.file-type-tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  margin-right: 6px;
}

.file-type-tag.pdf {
  background: #fef0f0;
  color: #f56c6c;
}

.file-type-tag.ppt {
  background: #fdf6ec;
  color: #e6a23c;
}

.file-size-tag {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.file-source {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
