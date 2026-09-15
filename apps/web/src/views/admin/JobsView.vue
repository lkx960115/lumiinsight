<template>
  <div>
    <div class="page-head">
      <div>
        <h2>任务日志</h2>
        <p class="muted">导入和分析跑得怎样。失败了去对应项目里重试。</p>
      </div>
      <div class="page-actions">
        <el-button text @click="load">刷新</el-button>
      </div>
    </div>

    <section class="product-section">
      <div class="section-head">
        <h3>导入</h3>
      </div>
      <div class="panel">
        <el-table :data="imports.records" empty-text="还没有导入记录。">
          <el-table-column label="文件" min-width="180">
            <template #default="{ row }">{{ row.filename }}</template>
          </el-table-column>
          <el-table-column label="项目" min-width="140">
            <template #default="{ row }">{{ projectName(row.projectId) }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="200">
            <template #default="{ row }">
              <span class="status-text" :class="{ 'is-ok': row.status === 'READY', 'is-bad': row.status === 'FAILED' }">
                {{ jobStatusLabel(row.status) }}
              </span>
              <p class="cell-note">{{ importRemark(row) }}</p>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="168">
            <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="88">
            <template #default="{ row }">
              <el-button v-if="row.projectId" text @click="openProject(row.projectId)">打开</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="product-section">
      <div class="section-head">
        <h3>分析</h3>
      </div>
      <div class="panel">
        <el-table :data="pipeline.records" empty-text="还没有分析记录。">
          <el-table-column label="类型" min-width="120">
            <template #default="{ row }">{{ jobTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="项目" min-width="140">
            <template #default="{ row }">{{ projectName(row.projectId) }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="220">
            <template #default="{ row }">
              <span class="status-text" :class="{ 'is-ok': row.status === 'READY', 'is-bad': row.status === 'FAILED' }">
                {{ jobStatusLabel(row.status) }}
              </span>
              <p class="cell-note">{{ pipelineRemark(row.message) }}</p>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="168">
            <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="88">
            <template #default="{ row }">
              <el-button v-if="row.projectId" text @click="openProject(row.projectId)">打开</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import { formatClock, importRemark, jobStatusLabel, jobTypeLabel, pipelineRemark } from '@/utils/labels'

const router = useRouter()
const imports = reactive({ records: [] as any[] })
const pipeline = reactive({ records: [] as any[] })
const names = ref<Record<number, string>>({})

function projectName(id?: number) {
  if (!id) return '—'
  return names.value[id] || '项目'
}

function openProject(id: number) {
  router.push(`/app/projects/${id}`)
}

async function load() {
  const [jobRes, pipeRes, projectRes] = await Promise.all([
    http.get('/admin/jobs', { params: { size: 50 } }),
    http.get('/admin/pipeline', { params: { size: 50 } }),
    http.get('/projects', { params: { page: 1, size: 100 } }),
  ])
  imports.records = jobRes.data.data.records
  pipeline.records = pipeRes.data.data.records
  const map: Record<number, string> = {}
  for (const item of projectRes.data.data.records || []) {
    map[item.id] = item.name
  }
  names.value = map
}

onMounted(load)
</script>
