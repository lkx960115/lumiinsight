<template>
  <div>
    <h2>任务日志</h2>
    <p class="muted">导入失败可下载失败行；分析失败会留下说明，可到项目里点重试。</p>
    <el-card class="block">
      <template #header>导入任务</template>
      <el-table :data="imports.records">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="projectId" label="项目" width="80" />
        <el-table-column prop="filename" label="文件" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="successRows" label="成功" width="80" />
        <el-table-column prop="failRows" label="失败" width="80" />
        <el-table-column prop="message" label="说明" />
      </el-table>
    </el-card>
    <el-card class="block">
      <template #header>
        <div class="page-head" style="margin: 0">
          <span>分析流水线</span>
          <el-button text @click="loadPipeline">刷新</el-button>
        </div>
      </template>
      <el-table :data="pipeline.records">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="projectId" label="项目" width="80" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span :class="{ 'job-failed': row.status === 'FAILED' }">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import http from '@/api/http'

const imports = reactive({ records: [] as any[] })
const pipeline = reactive({ records: [] as any[] })

function typeLabel(type: string) {
  if (type === 'CLEAN') return '清洗'
  if (type === 'ANALYZE') return '分析'
  if (type === 'RUN') return '清洗并分析'
  return type || '—'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '排队中',
    CLEANING: '清洗中',
    ANALYZING: '分析中',
    READY: '完成',
    FAILED: '失败',
  }
  return map[status] || status || '—'
}

async function loadPipeline() {
  const { data } = await http.get('/admin/pipeline', { params: { size: 50 } })
  pipeline.records = data.data.records
}

onMounted(async () => {
  const { data } = await http.get('/admin/jobs')
  imports.records = data.data.records
  await loadPipeline()
})
</script>
