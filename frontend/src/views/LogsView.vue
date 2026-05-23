<template>
  <div class="view-logs">
    <div class="hero">
      <h1>访问日志</h1>
      <p>查看文件的预览和下载记录</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>访问日志</h3>
        <p>记录文件下载、预览等操作的完整访问历史</p>
      </div>

      <el-form inline @submit.prevent>
        <el-form-item label="IP">
          <el-input
            v-model="logForm.ip"
            placeholder="按IP过滤"
            clearable
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="logForm.keyword"
            placeholder="文件名/论文名/学生"
            clearable
            style="width: 230px"
          />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="logForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="logsLoading" @click="loadLogs">
            查询日志
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" size="small" style="width: 100%" v-loading="logsLoading">
        <el-table-column label="时间" prop="accessed_at" width="180" />
        <el-table-column label="IP" prop="ip_address" width="150" />
        <el-table-column label="访问动作" width="100">
          <template #default="scope">
            <el-tag
              :type="actionTagType(scope.row.action)"
              size="small"
            >
              {{ actionLabel(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学生" prop="report_student_name" width="110" />
        <el-table-column label="汇报日期" prop="report_date" width="120" />
        <el-table-column label="论文标题" prop="paper_title" min-width="220" />
        <el-table-column label="文件名" prop="file_name" min-width="220" />
        <el-table-column label="类型" prop="file_type" width="80" />
      </el-table>

      <div v-if="logs.length" style="margin-top: 16px; display: flex; align-items: center; gap: 12px">
        <span style="font-size: 13px; color: var(--el-text-color-secondary)">
          共 {{ logs.length }} 条记录
        </span>
        <el-input
          v-model.number="limit"
          type="number"
          :min="1"
          :max="10000"
          style="width: 120px"
          size="small"
          placeholder="条数"
        />
        <el-button size="small" @click="loadLogs">刷新</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as logApi from '@/api/log'

defineOptions({ name: 'LogsView' })

const logs = ref([])
const logsLoading = ref(false)
const limit = ref(500)

const logForm = reactive({
  ip: '',
  keyword: '',
  dateRange: []
})

function normalizeDate(d) {
  if (!d) return ''
  if (typeof d === 'string') return d
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function actionLabel(action) {
  if (action === 'preview') return '预览'
  if (action === 'download') return '下载'
  return action || '-'
}

function actionTagType(action) {
  if (action === 'preview') return 'primary'
  if (action === 'download') return 'success'
  return 'info'
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const params = {}
    const ip = logForm.ip.trim()
    const keyword = logForm.keyword.trim()

    if (ip) params.ip = ip
    if (keyword) params.keyword = keyword
    if (limit.value) params.limit = limit.value

    if (logForm.dateRange?.length === 2) {
      params.start_date = normalizeDate(logForm.dateRange[0])
      params.end_date = normalizeDate(logForm.dateRange[1])
    }

    const data = await logApi.getLogs(params)
    logs.value = Array.isArray(data) ? data : []
  } catch (err) {
    ElMessage.error(err.message || '日志查询失败')
  } finally {
    logsLoading.value = false
  }
}

onMounted(() => {
  loadLogs()
})
</script>
