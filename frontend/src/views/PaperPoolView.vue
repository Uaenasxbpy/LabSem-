<template>
  <div class="view-paper-pool">
    <div class="hero">
      <h1>论文推荐池</h1>
      <p>推荐好论文，组会前认领汇报主题</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>论文推荐池</h3>
        <p>推荐好论文，组会前认领汇报主题</p>
      </div>

      <div class="pool-toolbar" style="margin-bottom: 14px; display: flex; align-items: center; gap: 12px">
        <el-radio-group v-model="poolTab" size="small" @change="loadPaperPool">
          <el-radio-button value="available">待认领</el-radio-button>
          <el-radio-button value="claimed">已认领</el-radio-button>
          <el-radio-button value="presented">已汇报</el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="small" @click="openAdd">
          <Icon name="file" :size="14" style="margin-right: 4px" /> 推荐论文
        </el-button>
      </div>

      <el-table
        :data="paperPool"
        size="small"
        style="width: 100%"
        v-loading="poolLoading"
      >
        <el-table-column label="论文标题" min-width="260">
          <template #default="scope">
            <a
              v-if="scope.row.url"
              :href="scope.row.url"
              target="_blank"
              class="pool-link"
            >
              {{ scope.row.title }}
            </a>
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
            <el-button
              v-if="scope.row.status === 'available'"
              link
              type="primary"
              @click="openClaimDialog(scope.row.id)"
            >
              认领
            </el-button>
            <el-button
              v-if="scope.row.status === 'claimed'"
              link
              type="warning"
              @click="handleUnclaim(scope.row)"
            >
              取消认领
            </el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">
              编辑
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Add / Edit dialog -->
    <el-dialog
      v-model="poolDialogVisible"
      :title="poolDialogMode === 'edit' ? '编辑论文' : '推荐论文'"
      width="520px"
    >
      <el-form label-width="72px">
        <el-form-item label="论文标题">
          <el-input v-model="poolForm.title" placeholder="论文标题" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="poolForm.url" placeholder="论文 URL（可选）" />
        </el-form-item>
        <el-form-item label="推荐人">
          <el-select
            v-model="poolForm.recommended_by"
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
        <el-form-item label="推荐理由">
          <el-input
            v-model="poolForm.notes"
            type="textarea"
            :rows="3"
            placeholder="为什么推荐这篇论文？（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poolDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePool">保存</el-button>
      </template>
    </el-dialog>

    <!-- Claim dialog -->
    <el-dialog v-model="claimDialogVisible" title="认领论文" width="380px">
      <el-form label-width="56px">
        <el-form-item label="姓名">
          <el-select
            v-model="claimName"
            filterable
            allow-create
            placeholder="选择或输入你的姓名"
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
      </el-form>
      <template #footer>
        <el-button @click="claimDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmClaim">确认认领</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as paperPoolApi from '@/api/paperPool'
import * as studentApi from '@/api/student'
import Icon from '@/components/Icon.vue'

defineOptions({ name: 'PaperPoolView' })

const students = ref([])
const paperPool = ref([])
const poolLoading = ref(false)
const poolTab = ref('available')

const poolDialogVisible = ref(false)
const poolDialogMode = ref('add')
const poolForm = reactive({
  id: null,
  title: '',
  url: '',
  recommended_by: '',
  notes: '',
})

const claimDialogVisible = ref(false)
const claimPaperId = ref(null)
const claimName = ref('')

const safeTrim = (value) => String(value ?? '').trim()

const poolStatusLabel = (status) => {
  if (status === 'available') return '待认领'
  if (status === 'claimed') return '已认领'
  if (status === 'presented') return '已汇报'
  return status
}

const poolStatusType = (status) => {
  if (status === 'available') return 'warning'
  if (status === 'claimed') return 'primary'
  if (status === 'presented') return 'success'
  return ''
}

const loadPaperPool = async () => {
  poolLoading.value = true
  try {
    const data = await paperPoolApi.getPaperPool({ status: poolTab.value })
    paperPool.value = data.papers || []
  } catch (err) {
    ElMessage.error(err.message || '加载论文池失败')
  } finally {
    poolLoading.value = false
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
  poolDialogMode.value = 'add'
  poolForm.id = null
  poolForm.title = ''
  poolForm.url = ''
  poolForm.recommended_by = ''
  poolForm.notes = ''
  poolDialogVisible.value = true
}

const openEdit = (item) => {
  poolDialogMode.value = 'edit'
  poolForm.id = item.id
  poolForm.title = item.title
  poolForm.url = item.url || ''
  poolForm.recommended_by = item.recommended_by
  poolForm.notes = item.notes || ''
  poolDialogVisible.value = true
}

const savePool = async () => {
  if (!safeTrim(poolForm.title)) return ElMessage.error('请填写论文标题')
  if (!safeTrim(poolForm.recommended_by)) return ElMessage.error('请填写推荐人')

  const payload = {
    title: safeTrim(poolForm.title),
    url: safeTrim(poolForm.url),
    recommended_by: safeTrim(poolForm.recommended_by),
    notes: safeTrim(poolForm.notes),
  }

  try {
    if (poolDialogMode.value === 'edit') {
      await paperPoolApi.updatePaperPool(poolForm.id, payload)
      ElMessage.success('已更新')
    } else {
      await paperPoolApi.createPaperPool(payload)
      ElMessage.success('已推荐')
    }
    poolDialogVisible.value = false
    await loadPaperPool()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  }
}

const handleDelete = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确认删除论文「${item.title}」？`,
      '删除论文',
      { type: 'warning' }
    )
    await paperPoolApi.deletePaperPool(item.id)
    ElMessage.success('已删除')
    await loadPaperPool()
  } catch (err) {
    if (String(err).includes('cancel') || String(err).includes('close')) return
    ElMessage.error(err.message || '删除失败')
  }
}

const openClaimDialog = (paperId) => {
  claimPaperId.value = paperId
  claimName.value = ''
  claimDialogVisible.value = true
}

const confirmClaim = async () => {
  if (!safeTrim(claimName.value)) return ElMessage.error('请填写你的姓名')
  try {
    await paperPoolApi.claimPaper(claimPaperId.value, safeTrim(claimName.value))
    ElMessage.success('认领成功')
    claimDialogVisible.value = false
    await loadPaperPool()
  } catch (err) {
    ElMessage.error(err.message || '认领失败')
  }
}

const handleUnclaim = async (item) => {
  try {
    await paperPoolApi.unclaimPaper(item.id)
    ElMessage.success('已取消认领')
    await loadPaperPool()
  } catch (err) {
    ElMessage.error(err.message || '取消失败')
  }
}

onMounted(async () => {
  await Promise.all([loadPaperPool(), loadStudents()])
})
</script>

<style scoped>
.pool-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
.pool-link:hover {
  text-decoration: underline;
}
</style>
