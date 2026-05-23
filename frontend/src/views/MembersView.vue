<template>
  <div class="view-members">
    <div class="hero">
      <h1>成员管理</h1>
      <p>管理实验室成员信息和邮箱</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>实验室成员管理</h3>
        <p>管理实验室成员邮箱，用于文献汇报邮件通知</p>
      </div>

      <el-button type="primary" @click="openAddMember" style="margin-bottom: 14px">
        <Icon name="user" :size="14" style="margin-right: 4px" />
        添加成员
      </el-button>

      <el-table :data="members" size="small" style="width: 100%" v-loading="membersLoading">
        <el-table-column label="姓名" prop="name" width="180" />
        <el-table-column label="邮箱" prop="email" />
        <el-table-column label="创建时间" prop="created_at" width="180" />
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="openEditMember(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteMember(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Add / Edit dialog -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogMode === 'edit' ? '编辑成员' : '添加成员'"
        width="420px"
      >
        <el-form ref="formRef" :model="memberForm" :rules="formRules" label-width="56px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="memberForm.name" placeholder="成员姓名" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="memberForm.email" placeholder="member@example.com" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSaveMember">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as memberApi from '@/api/member'
import Icon from '@/components/Icon.vue'

defineOptions({ name: 'MembersView' })

const members = ref([])
const membersLoading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('add')
const formRef = ref(null)

const memberForm = reactive({
  id: null,
  name: '',
  email: ''
})

const validateEmailUniqueness = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请填写邮箱'))
    return
  }
  const normalized = value.trim().toLowerCase()
  const duplicate = members.value.find(
    (m) => m.email.toLowerCase() === normalized && m.id !== memberForm.id
  )
  if (duplicate) {
    callback(new Error('该邮箱已被其他成员使用'))
  } else {
    callback()
  }
}

const formRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
    { validator: validateEmailUniqueness, trigger: 'blur' }
  ]
}

async function loadMembers() {
  membersLoading.value = true
  try {
    const data = await memberApi.getMembers()
    members.value = data.members || []
  } catch (err) {
    ElMessage.error(err.message || '加载成员失败')
  } finally {
    membersLoading.value = false
  }
}

function openAddMember() {
  dialogMode.value = 'add'
  memberForm.id = null
  memberForm.name = ''
  memberForm.email = ''
  dialogVisible.value = true
}

function openEditMember(member) {
  dialogMode.value = 'edit'
  memberForm.id = member.id
  memberForm.name = member.name
  memberForm.email = member.email
  dialogVisible.value = true
}

async function handleSaveMember() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const payload = {
      name: memberForm.name.trim(),
      email: memberForm.email.trim()
    }

    if (dialogMode.value === 'edit') {
      await memberApi.updateMember(memberForm.id, payload)
      ElMessage.success('已更新')
    } else {
      await memberApi.createMember(payload)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    await loadMembers()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDeleteMember(member) {
  try {
    await ElMessageBox.confirm(
      `确认删除成员 ${member.name}（${member.email}）？`,
      '删除成员',
      { type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await memberApi.deleteMember(member.id)
    ElMessage.success('已删除')
    await loadMembers()
  } catch (err) {
    ElMessage.error(err.message || '删除失败')
  }
}

onMounted(() => {
  loadMembers()
})
</script>
