<template>
  <div class="view-schedule">
    <div class="hero">
      <h1>组会排期</h1>
      <p>管理未来组会安排，指定汇报人与主题</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>组会排期</h3>
        <p>管理未来组会安排，指定汇报人与主题</p>
      </div>

      <div style="margin-bottom: 14px; display: flex; align-items: center; gap: 12px">
        <el-button type="primary" @click="openAddSchedule">
          <Icon name="calendar" :size="14" style="margin-right: 4px" /> 新建排期
        </el-button>
        <el-radio-group v-model="statusFilter" size="small" @change="loadSchedules">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="upcoming">待进行</el-radio-button>
          <el-radio-button value="completed">已完成</el-radio-button>
          <el-radio-button value="cancelled">已取消</el-radio-button>
        </el-radio-group>
      </div>

      <el-table
        :data="filteredSchedules"
        size="small"
        style="width: 100%"
        v-loading="scheduleLoading"
      >
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
            <el-button link type="primary" @click="openEditSchedule(scope.row)">
              编辑
            </el-button>
            <el-button
              v-if="scope.row.status === 'upcoming'"
              link
              type="success"
              @click="updateStatus(scope.row, 'completed')"
            >
              完成
            </el-button>
            <el-button
              v-if="scope.row.status === 'upcoming'"
              link
              type="warning"
              @click="updateStatus(scope.row, 'cancelled')"
            >
              取消
            </el-button>
            <el-button
              v-if="scope.row.status !== 'upcoming'"
              link
              type="primary"
              @click="updateStatus(scope.row, 'upcoming')"
            >
              恢复
            </el-button>
            <el-button link type="danger" @click="deleteSchedule(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Add / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑排期' : '新建排期'"
      width="520px"
    >
      <el-form label-width="72px">
        <el-form-item label="日期">
          <el-date-picker
            v-model="scheduleForm.meeting_date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="汇报人">
          <el-select
            v-model="scheduleForm.student_name"
            filterable
            allow-create
            placeholder="选择或输入姓名"
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
        <el-form-item label="主题">
          <el-input v-model="scheduleForm.topic" placeholder="汇报主题（可选）" />
        </el-form-item>
        <el-form-item label="形式">
          <el-select v-model="scheduleForm.meeting_format" style="width: 100%">
            <el-option label="线下" value="线下" />
            <el-option label="线上" value="线上" />
            <el-option label="线下+线上" value="线下+线上" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="scheduleForm.location" placeholder="如：网安楼329" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="scheduleForm.notes"
            type="textarea"
            :rows="2"
            placeholder="可选备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as scheduleApi from '@/api/schedule'
import * as studentApi from '@/api/student'
import Icon from '@/components/Icon.vue'

defineOptions({ name: 'ScheduleView' })

const students = ref([])
const schedules = ref([])
const scheduleLoading = ref(false)
const statusFilter = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('add')
const scheduleForm = reactive({
  id: null,
  meeting_date: '',
  student_name: '',
  topic: '',
  meeting_format: '线下',
  location: '',
  notes: '',
})

const safeTrim = (value) => String(value ?? '').trim()

const filteredSchedules = computed(() => {
  if (!statusFilter.value) return schedules.value
  return schedules.value.filter((s) => s.status === statusFilter.value)
})

const scheduleStatusLabel = (status) => {
  if (status === 'upcoming') return '待进行'
  if (status === 'completed') return '已完成'
  if (status === 'cancelled') return '已取消'
  return status
}

const scheduleStatusType = (status) => {
  if (status === 'upcoming') return 'primary'
  if (status === 'completed') return 'success'
  if (status === 'cancelled') return 'info'
  return ''
}

const loadSchedules = async () => {
  scheduleLoading.value = true
  try {
    const data = await scheduleApi.getSchedules()
    schedules.value = data.schedules || []
  } catch (err) {
    ElMessage.error(err.message || '加载排期失败')
  } finally {
    scheduleLoading.value = false
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

const openAddSchedule = () => {
  dialogMode.value = 'add'
  scheduleForm.id = null
  scheduleForm.meeting_date = ''
  scheduleForm.student_name = ''
  scheduleForm.topic = ''
  scheduleForm.meeting_format = '线下'
  scheduleForm.location = ''
  scheduleForm.notes = ''
  dialogVisible.value = true
}

const openEditSchedule = (item) => {
  dialogMode.value = 'edit'
  scheduleForm.id = item.id
  scheduleForm.meeting_date = item.meeting_date
  scheduleForm.student_name = item.student_name
  scheduleForm.topic = item.topic || ''
  scheduleForm.meeting_format = item.meeting_format
  scheduleForm.location = item.location || ''
  scheduleForm.notes = item.notes || ''
  dialogVisible.value = true
}

const saveSchedule = async () => {
  if (!safeTrim(scheduleForm.student_name)) return ElMessage.error('请填写汇报人')
  if (!scheduleForm.meeting_date) return ElMessage.error('请选择日期')

  const payload = {
    meeting_date: scheduleForm.meeting_date,
    student_name: safeTrim(scheduleForm.student_name),
    topic: safeTrim(scheduleForm.topic),
    meeting_format: scheduleForm.meeting_format,
    location: safeTrim(scheduleForm.location),
    notes: safeTrim(scheduleForm.notes),
  }

  try {
    if (dialogMode.value === 'edit') {
      await scheduleApi.updateSchedule(scheduleForm.id, payload)
      ElMessage.success('已更新')
    } else {
      await scheduleApi.createSchedule(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  }
}

const updateStatus = async (item, status) => {
  try {
    await scheduleApi.updateScheduleStatus(item.id, status)
    ElMessage.success('状态已更新')
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  }
}

const deleteSchedule = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${item.student_name} 在 ${item.meeting_date} 的排期？`,
      '删除排期',
      { type: 'warning' }
    )
    await scheduleApi.deleteSchedule(item.id)
    ElMessage.success('已删除')
    await loadSchedules()
  } catch (err) {
    if (String(err).includes('cancel') || String(err).includes('close')) return
    ElMessage.error(err.message || '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadSchedules(), loadStudents()])
})
</script>
