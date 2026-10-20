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
        <el-pagination
          class="pager"
          background
          hide-on-single-page
          layout="total, prev, pager, next"
          :page-size="20"
          :total="imports.total"
          v-model:current-page="importPage"
          @current-change="loadImports"
        />
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
        <el-pagination
          class="pager"
          background
          hide-on-single-page
          layout="total, prev, pager, next"
          :page-size="20"
          :total="pipeline.total"
          v-model:current-page="pipelinePage"
          @current-change="loadPipeline"
        />
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
const importPage = ref(1)
const pipelinePage = ref(1)
const imports = reactive({ records: [] as any[], total: 0 })
const pipeline = reactive({ records: [] as any[], total: 0 })
const names = ref<Record<number, string>>({})

function projectName(id?: number) {
  if (!id) return '—'
  return names.value[id] || '项目'
}

function openProject(id: number) {
  router.push(`/app/projects/${id}`)
}

async function loadImports() {
  const { data } = await http.get('/admin/jobs', { params: { page: importPage.value, size: 20 } })
  imports.records = data.data.records
  imports.total = Number(data.data.total)
}

async function loadPipeline() {
  const { data } = await http.get('/admin/pipeline', { params: { page: pipelinePage.value, size: 20 } })
  pipeline.records = data.data.records
  pipeline.total = Number(data.data.total)
}

async function loadNames() {
  const { data } = await http.get('/projects', { params: { page: 1, size: 100 } })
  const map: Record<number, string> = {}
  for (const item of data.data.records || []) {
    map[item.id] = item.name
  }
  names.value = map
}

async function load() {
  await Promise.all([loadImports(), loadPipeline(), loadNames()])
}

onMounted(load)
</script>
