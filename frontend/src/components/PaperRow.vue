<template>
  <div class="paper-row">
    <el-input
      :model-value="modelValue.title"
      placeholder="论文标题"
      @update:model-value="update('title', $event)"
    />
    <el-upload
      :show-file-list="false"
      :before-upload="handleFile"
      accept=".pdf"
    >
      <el-button :type="modelValue.file ? 'success' : 'default'" plain>
        <Icon name="file" :size="14" style="margin-right:4px" />
        {{ modelValue.file ? modelValue.file.name : '选择 PDF' }}
      </el-button>
    </el-upload>
    <el-button
      type="danger"
      plain
      @click="$emit('delete')"
    >
      <Icon name="trash" :size="14" />
    </el-button>
  </div>
</template>

<script setup>
import Icon from './Icon.vue'

const props = defineProps({
  index: { type: Number, required: true },
  modelValue: {
    type: Object,
    default: () => ({ title: '', file: null })
  }
})

const emit = defineEmits(['update:modelValue', 'delete'])

function update(field, value) {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

function handleFile(file) {
  emit('update:modelValue', { ...props.modelValue, file })
  return false
}
</script>
