<template>
  <div>
    <div class="page-head">
      <div>
        <h2>用量</h2>
        <p class="muted">只看调用次数和 token，不能改。不展示 Key 和评论文本。</p>
      </div>
      <div class="page-actions">
        <el-button text @click="refresh">刷新</el-button>
      </div>
    </div>

    <div class="overview-stats">
      <div class="overview-stat">
        <strong>{{ summary.total }}</strong>
        <span>调用次数</span>
      </div>
      <div class="overview-stat">
        <strong>{{ summary.success }}</strong>
        <span>成功</span>
      </div>
      <div class="overview-stat">
        <strong>{{ summary.fail }}</strong>
        <span>失败</span>
      </div>
      <div class="overview-stat">
        <strong>{{ summary.tokens }}</strong>
        <span>token 合计</span>
      </div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="purpose" clearable placeholder="全部用途" style="width: 160px" @change="search">
          <el-option label="方面情感" value="absa" />
          <el-option label="整句情感" value="sentiment" />
          <el-option label="报告摘要" value="report_summary" />
          <el-option label="向量检索" value="embedding" />
          <el-option label="去水辅助" value="spam_classify" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-button text @click="reset">重置</el-button>
      </div>
      <el-table :data="table.records" v-loading="loading" :empty-text="emptyText">
        <el-table-column label="用途" min-width="140">
          <template #default="{ row }">{{ purposeLabel(row.purposeCode) }}</template>
        </el-table-column>
        <el-table-column label="模型" min-width="160">
          <template #default="{ row }">{{ row.modelCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="用量" min-width="160">
          <template #default="{ row }">
            <div>{{ row.totalTokens || 0 }} token</div>
            <p class="cell-note">输入 {{ row.promptTokens || 0 }} · 输出 {{ row.completionTokens || 0 }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="200">
          <template #default="{ row }">
            <span class="status-text" :class="row.success === 1 ? 'is-ok' : 'is-bad'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </span>
            <p v-if="row.detail" class="cell-note">{{ friendlyMessage(row.detail) }}</p>
          </template>
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
import { computed, onMounted, reactive, ref } from 'vue'
import http from '@/api/http'
import { formatClock, friendlyMessage, purposeLabel } from '@/utils/labels'

const loading = ref(false)
const page = ref(1)
const purpose = ref('')
const table = reactive({ records: [] as any[], total: 0 })
const summary = reactive({ total: 0, success: 0, fail: 0, tokens: 0 })
const filtered = ref(false)
const emptyText = computed(() =>
  filtered.value ? '没有符合条件的调用记录。试试重置筛选。' : '还没有调用记录。分析跑过之后会出现。'
)

function search() {
  page.value = 1
  filtered.value = Boolean(purpose.value)
  load()
}

function reset() {
  purpose.value = ''
  filtered.value = false
  search()
}

async function refresh() {
  await Promise.all([load(), loadSummary()])
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/admin/llm/usage', {
      params: { page: page.value, size: 20, purpose: purpose.value || undefined },
    })
    table.records = data.data.records || []
    table.total = Number(data.data.total) || 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  const { data } = await http.get('/admin/llm/usage/summary')
  const row = data.data || {}
  summary.total = Number(row.total) || 0
  summary.success = Number(row.success) || 0
  summary.fail = Number(row.fail) || 0
  summary.tokens = Number(row.tokens) || 0
}

onMounted(refresh)
</script>
