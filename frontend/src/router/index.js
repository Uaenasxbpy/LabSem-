import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../components/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/search',
    children: [
      {
        path: 'upload',
        name: 'Upload',
        component: () => import('../views/UploadView.vue'),
        meta: { title: '文献上传' }
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('../views/SearchView.vue'),
        meta: { title: '文献搜索' }
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('../views/ScheduleView.vue'),
        meta: { title: '组会安排' }
      },
      {
        path: 'pool',
        name: 'PaperPool',
        component: () => import('../views/PaperPoolView.vue'),
        meta: { title: '论文池' }
      },
      {
        path: 'labfiles',
        name: 'LabFiles',
        component: () => import('../views/LabFilesView.vue'),
        meta: { title: '实验室文件' }
      },
      {
        path: 'members',
        name: 'Members',
        component: () => import('../views/MembersView.vue'),
        meta: { title: '成员管理' }
      },
      {
        path: 'email',
        name: 'SmtpConfig',
        component: () => import('../views/SmtpConfigView.vue'),
        meta: { title: '邮件配置' }
      },
      {
        path: 'compose',
        name: 'Compose',
        component: () => import('../views/ComposeView.vue'),
        meta: { title: '发送邮件' }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('../views/LogsView.vue'),
        meta: { title: '访问日志' }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '数据看板' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
