<template>
  <aside class="sidebar">
    <div class="brand">
      <h2>LabSem</h2>
      <p>文献管理系统</p>
    </div>

    <nav class="nav">
      <div class="nav-group">
        <span class="nav-group-label">文献管理</span>
        <router-link
          v-for="item in litRoutes"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: isActive(item.path) }"
        >
          <span class="icon"><Icon :name="item.icon" :size="18" /></span>
          {{ item.label }}
        </router-link>
      </div>

      <div class="nav-group">
        <span class="nav-group-label">邮件中心</span>
        <router-link
          v-for="item in mailRoutes"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: isActive(item.path) }"
        >
          <span class="icon"><Icon :name="item.icon" :size="18" /></span>
          {{ item.label }}
        </router-link>
      </div>
    </nav>

    <div class="sidebar-footer">
      LabSem v1.0
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import Icon from './Icon.vue'

const route = useRoute()

const litRoutes = [
  { path: '/upload', label: '文献上传', icon: 'upload' },
  { path: '/search', label: '文献搜索', icon: 'search' },
  { path: '/schedule', label: '组会安排', icon: 'calendar' },
  { path: '/pool', label: '论文池', icon: 'paper' },
  { path: '/labfiles', label: '实验室文件', icon: 'folder' }
]

const mailRoutes = [
  { path: '/members', label: '成员管理', icon: 'user' },
  { path: '/email', label: '邮件配置', icon: 'settings' },
  { path: '/compose', label: '发送邮件', icon: 'send' },
  { path: '/logs', label: '访问日志', icon: 'log' },
  { path: '/dashboard', label: '数据看板', icon: 'chart' }
]

function isActive(path) {
  return route.path === path
}
</script>
