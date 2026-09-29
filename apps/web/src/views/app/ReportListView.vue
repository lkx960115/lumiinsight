<template>
  <div>
    <div class="page-head">
      <div>
        <h2>报告</h2>
        <p class="muted">看单品结论和原声。每条结论都能点回原评，没有证据的句子不会出现。</p>
      </div>
    </div>
    <div class="panel">
      <el-table :data="table.records" v-loading="loading" empty-text="还没有报告。请到项目里先清洗并分析，完成后会生成报告。">
        <el-table-column label="报告" min-width="220">
          <template #default="{ row }">
            <a class="name-link" @click.prevent="router.push(`/app/reports/${row.id}`)">{{ row.title }}</a>
            <p class="cell-note">{{ row.projectName }}</p>
          </template>
        </el-table-column>
        <el-table-column label="品牌" min-width="100">
          <template #default="{ row }">{{ row.brand || '—' }}</template>
        </el-table-column>
        <el-table-column label="主型号" min-width="100">
          <template #default="{ row }">{{ row.mainModel || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="180">
          <template #default="{ row }">
            <span class="status-text" :class="{ 'is-ok': row.status === 'READY', 'is-bad': row.status === 'FAILED' }">
              {{ jobStatusLabel(row.status) }}
            </span>
            <p v-if="row.message" class="cell-note">{{ row.message }}</p>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="88">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text @click="router.push(`/app/reports/${row.id}`)">打开</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        hide-on-single-page
        layout="total, prev, pager, next"
        :page-size="10"
        :total="table.total"
        v-model:current-page="page"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import { formatClock, jobStatusLabel } from '@/utils/labels'

const router = useRouter()
const loading = ref(false)
const page = ref(1)
const table = reactive({ records: [] as any[], total: 0 })

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/reports', { params: { page: page.value, size: 10 } })
    table.records = data.data.records || []
    table.total = Number(data.data.total) || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
