<template>
  <div class="view-labfiles">
    <div class="hero">
      <h1>文件管理</h1>
      <p>管理实验室内部文件、模板和文档资源</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>文件管理</h3>
        <p>管理实验室内部文件、模板和文档资源</p>
      </div>

      <div class="labfiles-toolbar" style="margin-bottom: 14px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
        <el-form inline @submit.prevent style="flex: 1">
          <el-form-item>
            <el-input
              v-model="search.keyword"
              placeholder="搜索标题/描述"
              clearable
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item>
            <el-select
              v-model="search.tag"
              placeholder="按标签筛选"
              clearable
              filterable
              style="width: 160px"
              @change="loadLabFiles"
            >
              <el-option
                v-for="t in allTags"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="labFilesLoading"
              @click="loadLabFiles"
            >
              搜索
            </el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openAdd">
          <Icon name="upload" :size="14" style="margin-right: 4px" /> 上传文件
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
            <el-tag
              v-for="t in tagsList(f.tags)"
              :key="t"
              size="small"
              type="info"
              effect="plain"
              class="labfile-tag"
            >
              {{ t }}
            </el-tag>
          </div>
          <div class="labfile-file">{{ f.original_name }}</div>
          <div class="labfile-actions">
            <a
              :href="'/api/lab-files/' + f.id + '/download'"
              class="labfile-action-btn"
            >
              <Icon name="download" :size="13" /> 下载
            </a>
            <a
              :href="'/api/lab-files/' + f.id + '/preview'"
              target="_blank"
              class="labfile-action-btn"
            >
              <Icon name="eye" :size="13" /> 预览
            </a>
            <button class="labfile-action-btn" @click="openEdit(f)">
              <Icon name="file" :size="13" /> 编辑
            </button>
            <button class="labfile-action-btn danger" @click="handleDelete(f)">
              <Icon name="trash" :size="13" /> 删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Upload / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑文件信息' : '上传文件'"
      width="520px"
    >
      <el-form label-width="72px">
        <el-form-item label="标题">
          <el-input v-model="fileForm.title" placeholder="文件标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="fileForm.description"
            type="textarea"
            :rows="2"
            placeholder="文件描述（可选）"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input
            v-model="fileForm.tags"
            placeholder="多个标签用逗号分隔，如：模板,Word,开题报告"
          />
        </el-form-item>
        <el-form-item label="上传人">
          <el-select
            v-model="fileForm.uploaded_by"
            filterable
            allow-create
            clearable
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
        <el-form-item v-if="dialogMode === 'add'" label="选择文件">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="() => (fileForm.file = null)"
          >
            <el-button plain>选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLabFile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as labFileApi from '@/api/labFile'
import * as studentApi from '@/api/student'
import Icon from '@/components/Icon.vue'

defineOptions({ name: 'LabFilesView' })

const students = ref([])
const labFiles = ref([])
const labFilesLoading = ref(false)
const allTags = ref([])

const search = reactive({ keyword: '', tag: '' })

const dialogVisible = ref(false)
const dialogMode = ref('add')
const fileForm = reactive({
  id: null,
  title: '',
  description: '',
  tags: '',
  uploaded_by: '',
  file: null,
})

const safeTrim = (value) => String(value ?? '').trim()

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const tagsList = (tagsStr) => {
  if (!tagsStr) return []
  return tagsStr.split(',').map((t) => t.trim()).filter(Boolean)
}

const loadLabFiles = async () => {
  labFilesLoading.value = true
  try {
    const params = {}
    const kw = safeTrim(search.keyword)
    const tg = safeTrim(search.tag)
    if (kw) params.keyword = kw
    if (tg) params.tag = tg
    const data = await labFileApi.getLabFiles(params)
    labFiles.value = data.files || []
  } catch (err) {
    ElMessage.error(err.message || '加载文件失败')
  } finally {
    labFilesLoading.value = false
  }
}

const loadLabFileTags = async () => {
  try {
    const data = await labFileApi.getLabFileTags()
    allTags.value = data || []
  } catch {
    // silent
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

const openAdd = () => {
  dialogMode.value = 'add'
  fileForm.id = null
  fileForm.title = ''
  fileForm.description = ''
  fileForm.tags = ''
  fileForm.uploaded_by = ''
  fileForm.file = null
  dialogVisible.value = true
}

const openEdit = (item) => {
  dialogMode.value = 'edit'
  fileForm.id = item.id
  fileForm.title = item.title
  fileForm.description = item.description || ''
  fileForm.tags = item.tags || ''
  fileForm.uploaded_by = item.uploaded_by || ''
  fileForm.file = null
  dialogVisible.value = true
}

const onFileChange = (file) => {
  fileForm.file = file.raw || null
}

const saveLabFile = async () => {
  if (!safeTrim(fileForm.title)) return ElMessage.error('请填写文件标题')
  if (dialogMode.value === 'add' && !fileForm.file) return ElMessage.error('请选择文件')

  if (dialogMode.value === 'edit') {
    const payload = {
      title: safeTrim(fileForm.title),
      description: safeTrim(fileForm.description),
      tags: safeTrim(fileForm.tags),
      uploaded_by: safeTrim(fileForm.uploaded_by),
    }
    try {
      await labFileApi.updateLabFile(fileForm.id, payload)
      ElMessage.success('已更新')
      dialogVisible.value = false
      await loadLabFiles()
      await loadLabFileTags()
    } catch (err) {
      ElMessage.error(err.message || '保存失败')
    }
  } else {
    const formData = new FormData()
    formData.append('title', safeTrim(fileForm.title))
    formData.append('description', safeTrim(fileForm.description))
    formData.append('tags', safeTrim(fileForm.tags))
    formData.append('uploaded_by', safeTrim(fileForm.uploaded_by))
    if (fileForm.file) formData.append('file', fileForm.file)
    try {
      await labFileApi.createLabFile(formData)
      ElMessage.success('已上传')
      dialogVisible.value = false
      await loadLabFiles()
      await loadLabFileTags()
    } catch (err) {
      ElMessage.error(err.message || '保存失败')
    }
  }
}

const handleDelete = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确认删除文件「${item.title}」？`,
      '删除文件',
      { type: 'warning' }
    )
    await labFileApi.deleteLabFile(item.id)
    ElMessage.success('已删除')
    await loadLabFiles()
  } catch (err) {
    if (String(err).includes('cancel') || String(err).includes('close')) return
    ElMessage.error(err.message || '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadLabFiles(), loadLabFileTags(), loadStudents()])
})
</script>

<style scoped>
.labfiles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.labfile-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
  background: var(--el-bg-color);
}

.labfile-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.labfile-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.labfile-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.labfile-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
  line-height: 1.5;
}

.labfile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.labfile-tag {
  margin: 0;
}

.labfile-file {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
  word-break: break-all;
}

.labfile-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.labfile-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: var(--el-color-primary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  text-decoration: none;
}

.labfile-action-btn:hover {
  color: var(--el-color-primary-light-3);
}

.labfile-action-btn.danger {
  color: var(--el-color-danger);
}

.labfile-action-btn.danger:hover {
  color: var(--el-color-danger-light-3);
}
</style>
