<template>
  <div class="view-smtp">
    <div class="hero">
      <h1>邮件配置</h1>
      <p>配置 SMTP 邮件服务器参数</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>邮件发送设置</h3>
        <p>配置 SMTP 服务器信息，用于发送文献汇报通知邮件</p>
      </div>

      <el-form
        ref="formRef"
        :model="smtpConfig"
        label-width="110px"
        style="max-width: 560px"
        v-loading="pageLoading"
      >
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
          <el-input
            v-model="smtpConfig.password"
            type="password"
            show-password
            placeholder="留空则不修改"
            @focus="passwordTouched = true"
          />
        </el-form-item>

        <el-form-item label="发件人名称">
          <el-input v-model="smtpConfig.sender_name" placeholder="实验室文献系统" />
        </el-form-item>

        <el-form-item label="使用 SSL/TLS">
          <el-switch v-model="smtpConfig.use_tls" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as smtpConfigApi from '@/api/smtpConfig'

defineOptions({ name: 'SmtpConfigView' })

const pageLoading = ref(false)
const saving = ref(false)
const passwordTouched = ref(false)

const smtpConfig = reactive({
  host: '',
  port: 465,
  username: '',
  password: '',
  sender_name: '',
  use_tls: true
})

async function loadSmtpConfig() {
  pageLoading.value = true
  try {
    const data = await smtpConfigApi.getSmtpConfig()
    smtpConfig.host = data.host || ''
    smtpConfig.port = data.port || 465
    smtpConfig.username = data.username || ''
    smtpConfig.password = ''
    smtpConfig.sender_name = data.sender_name || ''
    smtpConfig.use_tls = data.use_tls !== false
    passwordTouched.value = false
  } catch (err) {
    ElMessage.error(err.message || '加载SMTP配置失败')
  } finally {
    pageLoading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      host: smtpConfig.host.trim(),
      port: String(smtpConfig.port),
      username: smtpConfig.username.trim(),
      sender_name: smtpConfig.sender_name.trim(),
      use_tls: String(smtpConfig.use_tls)
    }

    // Only include password if user actually touched the field
    if (passwordTouched.value) {
      payload.password = smtpConfig.password
    }

    await smtpConfigApi.updateSmtpConfig(payload)
    ElMessage.success('SMTP配置已保存')
    passwordTouched.value = false
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadSmtpConfig()
})
</script>
