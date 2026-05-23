<template>
  <div class="bar-chart">
    <div
      v-for="(item, idx) in data"
      :key="idx"
      class="bar-col"
    >
      <span class="bar-value">{{ item.value }}</span>
      <div
        class="bar-fill"
        :class="item.color || 'primary'"
        :style="{ height: barHeight(item.value) + '%' }"
      />
      <span class="bar-label" :title="item.label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
    // Each item: { label: string, value: number, color?: 'primary' | 'accent' }
  }
})

const maxValue = computed(() => {
  if (!props.data.length) return 1
  return Math.max(...props.data.map(d => d.value), 1)
})

function barHeight(value) {
  return Math.max((value / maxValue.value) * 100, 2)
}
</script>
