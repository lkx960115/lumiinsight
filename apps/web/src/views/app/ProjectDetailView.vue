<template>
  <div v-if="project">
    <div class="page-head">
      <div>
        <h2>{{ project.name }}</h2>
        <p class="muted">{{ project.brand }} {{ project.mainModel }} · 评论 {{ project.reviewCount }} 条{{ competitorNote }}</p>
      </div>
      <div class="page-actions">
        <el-button text @click="load">刷新</el-button>
        <el-button v-if="latestReport" text @click="router.push(`/app/reports/${latestReport.id}`)">打开报告</el-button>
        <el-button v-permission="'report:generate'" :disabled="busy" @click="generateReport">生成报告</el-button>
        <el-button v-permission="'pipeline:execute'" :disabled="busy" @click="triggerClean">仅清洗</el-button>
        <el-button v-permission="'pipeline:execute'" :disabled="busy" @click="triggerAnalyze">仅分析</el-button>
        <el-button v-permission="'pipeline:execute'" type="primary" :disabled="busy" @click="triggerRun">清洗并分析</el-button>
        <el-button @click="router.push('/app/projects')">返回列表</el-button>
      </div>
    </div>

    <div v-if="runningJob" class="run-banner is-running">
      <div>
        <strong>正在处理</strong>
        <p>{{ jobTypeLabel(runningJob.type) }}进行中（{{ jobStatusLabel(runningJob.status) }}）{{ runningNote ? '，' + runningNote : '' }}。可以离开此页，稍后再回来看结果。</p>
      </div>
    </div>
    <div v-else-if="latestFailed" class="run-banner is-failed">
      <div>
        <strong>上次没有完成</strong>
        <p>{{ latestFailedNote }}</p>
      </div>
      <el-button v-permission="'pipeline:execute'" :disabled="busy" @click="retryJob(latestFailed.id)">重试</el-button>
    </div>

    <el-card class="block">
      <template #header>竞品</template>
      <p class="muted">名称要和评论里的商品名对得上。改完后重新生成报告，对照才会更新。</p>
      <p v-if="competitorNames.length">{{ competitorNames.join('、') }}</p>
      <p v-else>还没有填写竞品。点编辑补上名称，并导入对应评论。</p>
      <div class="row-actions">
        <el-button v-permission="'project:edit'" text @click="openEdit">编辑</el-button>
      </div>
    </el-card>

    <el-card class="block">
      <template #header>评论概览</template>
      <p class="muted">按有效评论统计，重复/广告/过短不计入图表。点方面条可看对应原评；有评论时间时会显示按月走势。</p>
      <OverviewCharts :overview="overview" @select-aspect="onSelectAspect" />
    </el-card>

    <el-card class="block">
      <template #header>导入评论（Excel / CSV）</template>
      <p class="muted">模板列：平台、原文（必填），时间、商品ID、评论ID、点赞、作者匿名ID、商品名、链接。用 Excel 打开请重新下载样例，不要把乱码文件再导入。导入成功后可点「清洗并分析」。</p>
      <div class="toolbar">
        <el-button text tag="a" href="/sample-reviews.csv" download>下载样例</el-button>
        <el-upload :show-file-list="false" :http-request="upload" accept=".xlsx,.xls,.csv">
          <el-button v-permission="'import:execute'" type="primary">选择文件导入</el-button>
        </el-upload>
      </div>
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
        <el-select v-model="aspect" clearable placeholder="全部方面" style="width: 140px" @change="searchReviews">
          <el-option v-for="item in overview?.aspects || []" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
        <el-select v-model="platform" clearable placeholder="全部平台" style="width: 140px" @change="searchReviews">
          <el-option label="小红书" value="xiaohongshu" />
          <el-option label="京东" value="jd" />
          <el-option label="淘宝" value="taobao" />
          <el-option label="抖音" value="douyin" />
        </el-select>
        <el-select v-model="searchMode" style="width: 120px" @change="searchReviews">
          <el-option label="搜原文" value="text" />
          <el-option label="按意思找" value="meaning" />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          :placeholder="searchMode === 'meaning' ? '例如安装麻烦、色温偏冷' : '搜索原文'"
          style="width: 240px"
          @keyup.enter="searchReviews"
        />
        <el-button @click="searchReviews">查询</el-button>
        <el-button text @click="resetReviews">重置</el-button>
      </div>
      <el-table :data="reviews.records" v-loading="reviewsLoading" :empty-text="reviewEmptyText">
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
        v-if="searchMode !== 'meaning'"
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

    <el-dialog v-model="editVisible" title="编辑项目" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="主型号"><el-input v-model="form.mainModel" /></el-form-item>
        <el-form-item label="竞品"><el-input v-model="competitorsText" placeholder="逗号分隔，需与评论商品名对应" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="keywordsText" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="平台">
          <el-checkbox-group v-model="form.platforms">
            <el-checkbox label="xiaohongshu">小红书</el-checkbox>
            <el-checkbox label="jd">京东</el-checkbox>
            <el-checkbox label="taobao">淘宝</el-checkbox>
            <el-checkbox label="douyin">抖音</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProject">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import http from '@/api/http'
import OverviewCharts from './OverviewCharts.vue'
import { formatClock, formatDateTime, friendlyMessage, importRemark, isRunningStatus, jobStatusLabel, jobTypeLabel, pipelineRemark, platformLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const project = ref<any>(null)
const overview = ref<any>(null)
const jobs = reactive({ records: [] as any[] })
const pipeline = reactive({ records: [] as any[] })
const reviews = reactive({ records: [] as any[], total: 0 })
const platform = ref('')
const aspect = ref('')
const keyword = ref('')
const searchMode = ref<'text' | 'meaning'>('text')
const reviewPage = ref(1)
const reviewsLoading = ref(false)
const reviewLoadError = ref('')
const reportBusy = ref(false)
const submitting = ref(false)
const watchingJobId = ref<number | null>(null)
const latestReport = ref<any>(null)
const editVisible = ref(false)
const competitorsText = ref('')
const keywordsText = ref('')
const form = reactive({
  name: '',
  category: '',
  brand: '',
  mainModel: '',
  platforms: [] as string[],
})
let pollTimer: ReturnType<typeof setInterval> | null = null
const competitorNames = computed(() => {
  const rows = project.value?.competitors
  return Array.isArray(rows) ? rows.filter(Boolean) : []
})
const competitorNote = computed(() => {
  if (!competitorNames.value.length) return ''
  return ' · 竞品 ' + competitorNames.value.join('、')
})
const runningJob = computed(() => {
  const row = pipeline.records[0]
  return row && isRunningStatus(row.status) ? row : null
})
const busy = computed(() => reportBusy.value || submitting.value || Boolean(runningJob.value))
const latestFailed = computed(() => {
  if (runningJob.value) return null
  const row = pipeline.records[0]
  return row?.status === 'FAILED' ? row : null
})
const latestFailedNote = computed(() => {
  const note = pipelineRemark(latestFailed.value?.message)
  return !note || note === '—' ? '请点重试，或先确认分析服务已启动。' : note
})
const runningNote = computed(() => {
  const note = pipelineRemark(runningJob.value?.message)
  if (note && /已完成|第 \d+ \/ \d+ 步/.test(note)) return note
  return ''
})
const reviewEmptyText = computed(() => {
  if (reviewLoadError.value) return reviewLoadError.value
  if (searchMode.value === 'meaning') {
    if (!keyword.value.trim()) return '请输入要找的意思，例如安装麻烦、色温偏冷。'
    return '没有相近的原评。试试换个说法，或改回搜原文。'
  }
  if (aspect.value) return '这个方面还没有原评。点图表换一个方面，或重置筛选。'
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
  aspect.value = ''
  searchMode.value = 'text'
  reviewLoadError.value = ''
  searchReviews()
}

function onSelectAspect(name: string) {
  aspect.value = name
  searchReviews()
}

function splitNames(value: string) {
  return value.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
}

function openEdit() {
  const row = project.value
  if (!row) return
  form.name = row.name || ''
  form.category = row.category || ''
  form.brand = row.brand || ''
  form.mainModel = row.mainModel || ''
  form.platforms = [...(row.platforms || [])]
  competitorsText.value = competitorNames.value.join(',')
  keywordsText.value = (row.keywords || []).join(',')
  editVisible.value = true
}

async function saveProject() {
  await http.put(`/projects/${id}`, {
    name: form.name,
    category: form.category,
    brand: form.brand,
    mainModel: form.mainModel,
    competitors: splitNames(competitorsText.value),
    keywords: splitNames(keywordsText.value),
    platforms: form.platforms,
  })
  ElMessage.success('已保存')
  editVisible.value = false
  await load()
}

async function load() {
  const { data } = await http.get(`/projects/${id}`)
  project.value = data.data
  await Promise.all([loadJobs(), loadPipeline(), loadReviews(), loadOverview(), loadLatestReport()])
}

async function loadOverview() {
  try {
    const { data } = await http.get(`/projects/${id}/overview`)
    overview.value = data.data
  } catch {
    overview.value = null
  }
}

async function loadLatestReport() {
  try {
    const { data } = await http.get(`/projects/${id}/reports/latest`)
    latestReport.value = data.data
  } catch {
    latestReport.value = null
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
  reviewsLoading.value = true
  reviewLoadError.value = ''
  try {
    if (searchMode.value === 'meaning') {
      const q = keyword.value.trim()
      if (!q) {
        reviews.records = []
        reviews.total = 0
        return
      }
      const { data } = await http.get(`/projects/${id}/reviews/semantic`, {
        params: {
          q,
          platform: platform.value || undefined,
          aspect: aspect.value || undefined,
        },
      })
      reviews.records = data.data.records || []
      reviews.total = Number(data.data.total || 0)
      return
    }
    const { data } = await http.get(`/projects/${id}/reviews`, {
      params: {
        page: reviewPage.value,
        size: 10,
        platform: platform.value || undefined,
        keyword: keyword.value || undefined,
        aspect: aspect.value || undefined,
      },
    })
    reviews.records = data.data.records
    reviews.total = Number(data.data.total)
  } catch (err) {
    reviews.records = []
    reviews.total = 0
    reviewLoadError.value = apiErrorMessage(err) || (searchMode.value === 'meaning'
      ? '按意思查找失败。请确认已配置向量检索模型，并完成清洗并分析。'
      : '没有符合条件的评论。试试重置筛选。')
  } finally {
    reviewsLoading.value = false
  }
}

function apiErrorMessage(err: unknown) {
  if (!err || typeof err !== 'object') return ''
  const row = err as { message?: string; response?: { data?: { message?: string } } }
  return friendlyMessage(row.response?.data?.message || row.message)
}

async function upload(opt: UploadRequestOptions) {
  const form = new FormData()
  form.append('file', opt.file)
  await http.post(`/projects/${id}/imports`, form)
  ElMessage.success('已提交导入任务')
  setTimeout(load, 800)
}

async function generateReport() {
  if (busy.value) return
  reportBusy.value = true
  try {
    const { data } = await http.post(`/projects/${id}/reports`)
    ElMessage.success('已生成报告')
    router.push(`/app/reports/${data.data.id}`)
  } finally {
    reportBusy.value = false
  }
}

async function triggerClean() {
  await runPipeline(`/projects/${id}/pipeline/clean`, '已开始清洗，可以离开此页')
}

async function triggerAnalyze() {
  await runPipeline(`/projects/${id}/pipeline/analyze`, '已开始分析，可以离开此页')
}

async function triggerRun() {
  await runPipeline(`/projects/${id}/pipeline/run`, '已开始清洗并分析，可以离开此页')
}

async function retryJob(jobId: number) {
  await runPipeline(`/pipeline/${jobId}/retry`, '已开始重试，可以离开此页')
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPoll() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    tickPipeline()
  }, 2000)
}

async function tickPipeline() {
  await loadPipeline()
  const latest = pipeline.records[0]
  if (latest && isRunningStatus(latest.status)) {
    return
  }
  const watched = watchingJobId.value
  if (watched && (!latest || Number(latest.id) !== watched)) {
    return
  }
  stopPoll()
  watchingJobId.value = null
  await load()
  if (!watched || !latest || Number(latest.id) !== watched) {
    return
  }
  if (latest.status === 'FAILED') {
    ElMessage.error(friendlyMessage(latest.message) || '没有完成')
  } else if (latest.status === 'READY') {
    const note = pipelineRemark(latest.message)
    ElMessage.success(!note || note === '—' ? '已完成' : note)
  }
}

async function runPipeline(url: string, pending: string) {
  if (busy.value) {
    return
  }
  submitting.value = true
  try {
    const { data } = await http.post(url)
    watchingJobId.value = Number(data.data?.id) || null
    ElMessage.success(pending)
    startPoll()
    await tickPipeline()
  } finally {
    submitting.value = false
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

onMounted(async () => {
  await load()
  if (runningJob.value) {
    watchingJobId.value = Number(runningJob.value.id) || null
    startPoll()
  }
})
onUnmounted(stopPoll)
</script>
