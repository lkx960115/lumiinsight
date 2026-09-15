<template>
  <div v-if="project">
    <div class="page-head">
      <div>
        <h2>{{ project.name }}</h2>
        <p class="muted">{{ project.brand }} {{ project.mainModel }} · 评论 {{ project.reviewCount }} 条</p>
      </div>
      <div class="page-actions">
        <el-button v-permission="'pipeline:execute'" :disabled="busy" @click="triggerClean">仅清洗</el-button>
        <el-button v-permission="'pipeline:execute'" :disabled="busy" @click="triggerAnalyze">仅分析</el-button>
        <el-button v-permission="'pipeline:execute'" type="primary" :disabled="busy" @click="triggerRun">清洗并分析</el-button>
        <el-button @click="router.push('/app/projects')">返回列表</el-button>
      </div>
    </div>

    <el-card class="block">
      <template #header>评论概览</template>
      <p class="muted">按有效评论统计，重复/广告/过短不计入图表，避免把声量放大。</p>
      <OverviewCharts :overview="overview" />
    </el-card>

    <el-card class="block">
      <template #header>导入评论（Excel / CSV）</template>
      <p class="muted">模板列：平台、原文（必填），时间、商品ID、评论ID、点赞、作者匿名ID、商品名、链接。导入成功后可点「清洗并分析」。</p>
      <el-upload :show-file-list="false" :http-request="upload" accept=".xlsx,.xls,.csv">
        <el-button v-permission="'import:execute'" type="primary">选择文件导入</el-button>
      </el-upload>
      <el-table :data="jobs.records" class="mt" empty-text="还没有导入记录。">
        <el-table-column label="文件" min-width="180">
          <template #default="{ row }">{{ row.filename }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="200">
          <template #default="{ row }">
            <span class="status-text" :class="{ 'is-ok': row.status === 'READY', 'is-bad': row.status === 'FAILED' }">
              {{ jobStatusLabel(row.status) }}
            </span>
            <p v-if="importRemark(row) !== '—'" class="cell-note">{{ importRemark(row) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.errorObjectKey" text @click="downloadErrors(row.id)">下载失败行</el-button>
            <el-button
              v-if="Number(row.successRows) > 0"
              v-permission="'pipeline:execute'"
              text
              :disabled="busy"
              @click="triggerRun"
            >
              清洗并分析
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>分析流水线</template>
      <p class="muted">一条导入可一键清洗并分析。失败任务会留下说明，可点重试。点开评论左侧箭头可看方面-情感-原因和置信度。方面名必须落在词典或「其它」。</p>
      <el-table :data="pipeline.records" class="mt" empty-text="还没有分析记录。">
        <el-table-column label="类型" min-width="120">
          <template #default="{ row }">{{ jobTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="220">
          <template #default="{ row }">
            <span class="status-text" :class="{ 'is-ok': row.status === 'READY', 'is-bad': row.status === 'FAILED' }">
              {{ jobStatusLabel(row.status) }}
            </span>
            <p v-if="pipelineRemark(row.message) !== '—'" class="cell-note">{{ pipelineRemark(row.message) }}</p>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="88">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              v-permission="'pipeline:execute'"
              text
              :disabled="busy"
              @click="retryJob(row.id)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>评论列表</template>
      <div class="toolbar">
        <el-select v-model="platform" clearable placeholder="全部平台" style="width: 140px" @change="searchReviews">
          <el-option label="小红书" value="xiaohongshu" />
          <el-option label="京东" value="jd" />
          <el-option label="淘宝" value="taobao" />
          <el-option label="抖音" value="douyin" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="搜索原文" style="width: 240px" @keyup.enter="searchReviews" />
        <el-button @click="searchReviews">查询</el-button>
        <el-button text @click="resetReviews">重置</el-button>
      </div>
      <el-table :data="reviews.records" :empty-text="reviewEmptyText">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.aspects?.length" class="aspect-detail">
              <div v-for="item in row.aspects" :key="item.name" class="aspect-line">
                <el-tag size="small" :type="sentimentType(item.sentiment)">{{ sentimentLabel(item.sentiment) }}</el-tag>
                <strong>{{ item.name }}</strong>
                <span>{{ item.reason || '—' }}</span>
                <span class="muted">置信度 {{ confText(item.confidence) }}</span>
              </div>
            </div>
            <p v-else class="muted">尚未分析。导入后点「清洗并分析」。展开本行可看方面、情感、原因和置信度。</p>
            <p v-if="cleanTagList(row).length || row.cleanReason || row.likeCount" class="muted">
              <template v-if="cleanTagList(row).length">清洗 {{ cleanTagList(row).map(tagLabel).join('、') }}</template>
              <template v-if="row.cleanReason"> · {{ row.cleanReason }}</template>
              <template v-if="row.likeCount"> · 点赞 {{ row.likeCount }}</template>
            </p>
          </template>
        </el-table-column>
        <el-table-column label="评论" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
        <el-table-column label="平台" min-width="160">
          <template #default="{ row }">
            <div>{{ platformLabel(row.platform) }}</div>
            <p v-if="aspectDisplay(row).length" class="cell-note">{{ aspectDisplay(row).map((item) => item.name).join('、') }}</p>
          </template>
        </el-table-column>
        <el-table-column label="情感" width="88">
          <template #default="{ row }">
            <span v-if="row.sentiment" class="status-text" :class="{ 'is-ok': row.sentiment === 'pos', 'is-bad': row.sentiment === 'neg' }">
              {{ sentimentLabel(row.sentiment) }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ formatDateTime(row.reviewTime) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        hide-on-single-page
        layout="total, prev, pager, next"
        :page-size="10"
        :total="reviews.total"
        v-model:current-page="reviewPage"
        @current-change="loadReviews"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import http from '@/api/http'
import OverviewCharts from './OverviewCharts.vue'
import { formatClock, formatDateTime, importRemark, jobStatusLabel, jobTypeLabel, pipelineRemark, platformLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const project = ref<any>(null)
const overview = ref<any>(null)
const jobs = reactive({ records: [] as any[] })
const pipeline = reactive({ records: [] as any[] })
const reviews = reactive({ records: [] as any[], total: 0 })
const platform = ref('')
const keyword = ref('')
const reviewPage = ref(1)
const busy = ref(false)
const reviewEmptyText = computed(() => {
  if (platform.value || keyword.value) return '没有符合条件的评论。试试重置筛选。'
  return '还没有评论。请先导入文件，再点清洗并分析。'
})

function searchReviews() {
  reviewPage.value = 1
  loadReviews()
}

function resetReviews() {
  platform.value = ''
  keyword.value = ''
  searchReviews()
}

async function load() {
  const { data } = await http.get(`/projects/${id}`)
  project.value = data.data
  await Promise.all([loadJobs(), loadPipeline(), loadReviews(), loadOverview()])
}

async function loadOverview() {
  try {
    const { data } = await http.get(`/projects/${id}/overview`)
    overview.value = data.data
  } catch {
    overview.value = null
  }
}

async function loadJobs() {
  const { data } = await http.get(`/projects/${id}/imports`)
  jobs.records = data.data.records
}

async function loadPipeline() {
  const { data } = await http.get(`/projects/${id}/pipeline`)
  pipeline.records = data.data.records
}

async function loadReviews() {
  const { data } = await http.get(`/projects/${id}/reviews`, {
    params: { page: reviewPage.value, size: 10, platform: platform.value || undefined, keyword: keyword.value || undefined },
  })
  reviews.records = data.data.records
  reviews.total = Number(data.data.total)
}

async function upload(opt: UploadRequestOptions) {
  const form = new FormData()
  form.append('file', opt.file)
  await http.post(`/projects/${id}/imports`, form)
  ElMessage.success('已提交导入任务')
  setTimeout(load, 800)
}

async function triggerClean() {
  await runPipeline(`/projects/${id}/pipeline/clean`, '正在清洗评论…', false)
}

async function triggerAnalyze() {
  await runPipeline(`/projects/${id}/pipeline/analyze`, '正在分析情感与方面…', true)
}

async function triggerRun() {
  await runPipeline(`/projects/${id}/pipeline/run`, '正在清洗并分析…', true)
}

async function retryJob(jobId: number) {
  await runPipeline(`/pipeline/${jobId}/retry`, '正在重试失败任务…', true)
}

async function runPipeline(url: string, pending: string, longWait: boolean) {
  if (busy.value) {
    return
  }
  busy.value = true
  const beforeId = pipeline.records[0]?.id
  try {
    await http.post(url)
    ElMessage.success(pending)
    const rounds = longWait ? 120 : 40
    const waitMs = longWait ? 2000 : 400
    for (let i = 0; i < rounds; i++) {
      await new Promise((r) => setTimeout(r, waitMs))
      await loadPipeline()
      const latest = pipeline.records[0]
      if (!latest || latest.id === beforeId) {
        continue
      }
      if (latest.status === 'FAILED') {
        ElMessage.error(latest.message || '任务失败')
        await load()
        return
      }
      if (latest.status === 'READY') {
        await load()
        ElMessage.success(latest.message || '完成')
        return
      }
    }
    await load()
    ElMessage.warning('任务仍在跑，请稍后刷新查看说明')
  } finally {
    busy.value = false
  }
}

function cleanTagList(row: any): string[] {
  return String(row?.cleanTags || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function tagLabel(tag: string) {
  const map: Record<string, string> = {
    duplicate: '重复',
    ad: '广告',
    short: '过短',
    masked: '已脱敏',
    template: '模板好评',
  }
  return map[tag] || tag
}

function aspectList(row: any): string[] {
  return String(row?.aspectHits || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

function aspectDisplay(row: any): { name: string; sentiment?: string }[] {
  if (row?.aspects?.length) {
    return row.aspects
  }
  return aspectList(row).map((name) => ({ name }))
}

function sentimentLabel(value: string) {
  const map: Record<string, string> = { pos: '正向', neg: '负向', neu: '中性' }
  return map[value] || '—'
}

function sentimentType(value: string) {
  if (value === 'pos') return 'success'
  if (value === 'neg') return 'danger'
  return 'info'
}

function confText(value: unknown) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return Math.round(n * 100) + '%'
}

async function downloadErrors(jobId: number) {
  const resp = await http.get(`/imports/${jobId}/errors`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'import-errors.csv'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>
