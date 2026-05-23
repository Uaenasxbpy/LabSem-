<template>
  <div class="file-preview">
    <div class="preview-tip">
      <Icon name="eye" :size="14" style="margin-right:6px" />
      文件预览中。如果内容无法显示，请
      <a :href="downloadUrl" target="_blank">点击下载</a>。
    </div>
    <div class="preview-actions" style="margin-bottom:12px">
      <el-button size="small" type="primary" plain>
        <a :href="downloadUrl" target="_blank" style="color:inherit;text-decoration:none;display:flex;align-items:center;gap:4px">
          <Icon name="download" :size="14" /> 下载文件
        </a>
      </el-button>
    </div>
    <iframe
      :src="previewUrl"
      class="preview-frame"
      frameborder="0"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'
import { getFilePreviewUrl, getFileDownloadUrl } from '../api/file'

const props = defineProps({
  fileId: { type: [Number, String], required: true },
  fileName: { type: String, default: '' }
})

const previewUrl = computed(() => getFilePreviewUrl(props.fileId))
const downloadUrl = computed(() => getFileDownloadUrl(props.fileId))
</script>
