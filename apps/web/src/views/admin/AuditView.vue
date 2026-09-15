<template>
  <div>
    <div class="page-head">
      <div>
        <h2>操作日志</h2>
        <p class="muted">谁在什么时候改了项目、模型和导入。</p>
      </div>
      <div class="page-actions">
        <el-button text @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel">
      <el-table :data="table.records" empty-text="还没有操作记录。">
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <div>{{ auditActionLabel(row.action) }}</div>
            <p class="cell-note">{{ row.username || '—' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="对象" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ auditDetailLabel(row.action, row.detail) || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        hide-on-single-page
        layout="total, prev, pager, next"
        :page-size="20"
        :total="table.total"
        v-model:current-page="page"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import http from '@/api/http'
import { auditActionLabel, auditDetailLabel, formatClock } from '@/utils/labels'

const page = ref(1)
const table = reactive({ records: [] as any[], total: 0 })

async function load() {
  const { data } = await http.get('/admin/audit', { params: { page: page.value, size: 20 } })
  table.records = data.data.records
  table.total = Number(data.data.total)
}

onMounted(load)
</script>
