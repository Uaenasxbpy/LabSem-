<template>
  <div class="view-dashboard">
    <div class="hero">
      <h1>数据看板</h1>
      <p>查看文献汇报和论文统计</p>
    </div>

    <div class="panel">
      <div class="section-header">
        <h3>数据看板</h3>
        <p>组会数据统计与趋势分析</p>
      </div>

      <!-- Stats cards -->
      <div class="dashboard-stats" v-loading="dashboardLoading">
        <div class="stat-card">
          <div class="stat-value">{{ dashboardStats.total_reports }}</div>
          <div class="stat-label">总汇报次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ dashboardStats.total_papers }}</div>
          <div class="stat-label">总论文数量</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ dashboardStats.total_members }}</div>
          <div class="stat-label">实验室成员</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ dashboardStats.monthly_reports }}</div>
          <div class="stat-label">本月汇报</div>
        </div>
      </div>

      <!-- Charts -->
      <el-row :gutter="20">
        <el-col :xs="24" :md="12">
          <div class="section-header" style="margin-top: 8px">
            <h3>学生排行榜</h3>
            <p>按汇报次数排序</p>
          </div>
          <BarChart v-if="studentChartData.length" :data="studentChartData" />
          <el-empty v-else description="暂无数据" :image-size="48" />
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="section-header" style="margin-top: 8px">
            <h3>月度趋势</h3>
            <p>近 12 个月汇报与论文数量</p>
          </div>
          <BarChart v-if="monthlyChartData.length" :data="monthlyChartData" />
          <el-empty v-else description="暂无数据" :image-size="48" />
        </el-col>
      </el-row>

      <!-- Detail table -->
      <div v-if="dashboardByStudent.length" style="margin-top: 20px">
        <el-table :data="dashboardByStudent" size="small" style="width: 100%">
          <el-table-column label="学生" prop="student_name" />
          <el-table-column label="汇报次数" prop="report_count" width="120" />
          <el-table-column label="论文数量" prop="paper_count" width="120" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as dashboardApi from '@/api/dashboard'
import BarChart from '@/components/BarChart.vue'

defineOptions({ name: 'DashboardView' })

const dashboardLoading = ref(false)
const dashboardStats = reactive({
  total_reports: 0,
  total_papers: 0,
  total_members: 0,
  monthly_reports: 0
})
const dashboardByStudent = ref([])
const dashboardMonthly = ref([])

const studentChartData = computed(() =>
  dashboardByStudent.value.map((s) => ({
    label: s.student_name,
    value: s.report_count,
    color: 'primary'
  }))
)

const monthlyChartData = computed(() =>
  dashboardMonthly.value.map((m) => ({
    label: m.month ? m.month.slice(5) : '',
    value: m.report_count,
    color: 'primary'
  }))
)

async function loadDashboard() {
  dashboardLoading.value = true
  try {
    const [stats, byStudent, monthly] = await Promise.all([
      dashboardApi.getStats(),
      dashboardApi.getByStudent(),
      dashboardApi.getMonthly()
    ])

    Object.assign(dashboardStats, {
      total_reports: stats.total_reports || 0,
      total_papers: stats.total_papers || 0,
      total_members: stats.total_members || 0,
      monthly_reports: stats.monthly_reports || 0
    })

    dashboardByStudent.value = Array.isArray(byStudent) ? byStudent : []
    dashboardMonthly.value = Array.isArray(monthly) ? monthly : []
  } catch (err) {
    ElMessage.error(err.message || '加载看板数据失败')
  } finally {
    dashboardLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.dashboard-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
</style>
