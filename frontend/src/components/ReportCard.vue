<template>
  <el-card shadow="never" class="report-card">
    <template #header>
      <div class="card-head">
        <div class="meta-line">
          <span>
            <Icon name="user" :size="14" />
            <strong>{{ report.student_name }}</strong>
          </span>
          <span>
            <Icon name="calendar" :size="14" />
            {{ report.report_date }}
          </span>
        </div>
        <div class="preview-actions">
          <el-button size="small" plain @click="$emit('preview', report)">
            <Icon name="eye" :size="14" style="margin-right:4px" /> 预览
          </el-button>
          <el-button size="small" type="danger" plain @click="$emit('delete', report.id)">
            <Icon name="trash" :size="14" />
          </el-button>
        </div>
      </div>
    </template>

    <div v-if="report.papers && report.papers.length">
      <div v-for="(paper, idx) in report.papers" :key="idx" class="paper-item">
        <span class="paper-index">{{ idx + 1 }}.</span>
        <span class="paper-title">{{ paper.title }}</span>
      </div>
    </div>

    <div v-if="report.files && report.files.length" class="file-links">
      <a
        v-for="file in report.files"
        :key="file.id"
        :href="getFileDownloadUrl(file.id)"
        target="_blank"
      >
        <Icon name="file" :size="13" />
        {{ file.original_name }}
      </a>
    </div>
  </el-card>
</template>

<script setup>
import Icon from './Icon.vue'
import { getFileDownloadUrl } from '../api/file'

defineProps({
  report: { type: Object, required: true }
})

defineEmits(['delete', 'preview'])
</script>
