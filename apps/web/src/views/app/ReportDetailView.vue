<template>
  <div v-if="report">
    <div class="page-head">
      <div>
        <h2>{{ report.title }}</h2>
        <p class="muted">{{ report.brand }} {{ report.mainModel }} · 每条结论都绑定了原评。竞品可并排看声量、方面和按月走势；点「查看原评」核原文。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="exportVisible = true">导出</el-button>
        <el-button v-permission="'report:generate'" :disabled="busy" @click="regenerate">生成报告</el-button>
        <el-button @click="router.push(`/app/projects/${report.projectId}`)">去项目</el-button>
        <el-button @click="router.push('/app/reports')">返回列表</el-button>
      </div>
    </div>

    <el-card v-if="report.snapshot" class="block">
      <template #header>声量与情感</template>
      <div class="overview-stats">
        <div class="overview-stat">
          <strong>{{ report.snapshot.imported }}</strong>
          <span>已导入</span>
        </div>
        <div class="overview-stat">
          <strong>{{ report.snapshot.counted }}</strong>
          <span>有效评论</span>
        </div>
        <div class="overview-stat">
          <strong>{{ report.snapshot.analyzed }}</strong>
          <span>已分析</span>
        </div>
        <div class="overview-stat">
          <strong>{{ report.snapshot.pos }}</strong>
          <span>正向</span>
        </div>
        <div class="overview-stat">
          <strong>{{ report.snapshot.neg }}</strong>
          <span>负向</span>
        </div>
        <div class="overview-stat">
          <strong>{{ report.snapshot.neu }}</strong>
          <span>中性</span>
        </div>
      </div>
    </el-card>

    <el-card v-for="section in sections" :key="section.code" class="block">
      <template #header>{{ section.title }}</template>
      <p v-if="section.code === 'compete'" class="muted">按项目里的竞品名称和评论商品名对照。每个竞品单独一行，没有对应原评的不进表。下面按方面看谁负向更集中，再按月看谁在变差。每句都能查看原评。改完竞品后请重新生成报告。</p>
      <p v-if="section.code === 'ask'" class="muted">按现成问法对照本项目原评写成。没有足够原评的问题不会出现。右侧仍可现场再问。</p>
      <el-table v-if="section.compare && section.compare.length" :data="section.compare" class="mt" empty-text="还没有对照数据。">
        <el-table-column label="对象" min-width="140">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="原评" width="88">
          <template #default="{ row }">{{ row.volume }}</template>
        </el-table-column>
        <el-table-column label="正向" width="88">
          <template #default="{ row }">{{ row.pos }}</template>
        </el-table-column>
        <el-table-column label="负向" width="88">
          <template #default="{ row }">{{ row.neg }}</template>
        </el-table-column>
        <el-table-column label="中性" width="88">
          <template #default="{ row }">{{ row.neu }}</template>
        </el-table-column>
        <el-table-column label="主要痛点" min-width="120">
          <template #default="{ row }">{{ row.topPain || '—' }}</template>
        </el-table-column>
      </el-table>
      <el-table
        v-if="section.aspectCompare && section.aspectCompare.length"
        :data="section.aspectCompare"
        class="mt"
        empty-text="这些方面还没有对照原评。"
      >
        <el-table-column label="方面" min-width="120">
          <template #default="{ row }">{{ row.aspect }}</template>
        </el-table-column>
        <el-table-column
          v-for="product in aspectProducts(section)"
          :key="product"
          :label="product"
          min-width="140"
        >
          <template #default="{ row }">{{ aspectCell(row, product) }}</template>
        </el-table-column>
      </el-table>
      <el-table
        v-if="section.monthCompare && section.monthCompare.length"
        :data="section.monthCompare"
        class="mt"
        empty-text="还没有带时间的对照原评。"
      >
        <el-table-column label="月份" min-width="120">
          <template #default="{ row }">{{ formatMonth(row.month) }}</template>
        </el-table-column>
        <el-table-column
          v-for="product in monthProducts(section)"
          :key="product"
          :label="product"
          min-width="160"
        >
          <template #default="{ row }">{{ monthCell(row, product) }}</template>
        </el-table-column>
      </el-table>
      <div v-for="(claim, idx) in section.claims || []" :key="idx" class="report-claim">
        <p>{{ claim.text }}</p>
        <div class="row-actions">
          <el-button v-if="(claim.evidenceIds || []).length" text @click="openClaim(claim)">查看原评</el-button>
        </div>
      </div>
      <div v-if="section.followUp" class="row-actions">
        <el-button text @click="router.push(`/app/projects/${report.projectId}`)">{{ section.followUp }}</el-button>
      </div>
    </el-card>
    <el-card v-if="!hasCompete" class="block">
      <template #header>竞品对照</template>
      <p class="muted">这份报告还没有竞品对照。在项目详情填写竞品，并导入带对应商品名的评论后，再点「生成报告」。</p>
      <div class="row-actions">
        <el-button text @click="router.push(`/app/projects/${report.projectId}`)">去项目</el-button>
      </div>
    </el-card>

    <el-dialog v-model="exportVisible" title="导出报告" width="520px">
      <p class="muted">PDF 方便发给领导；表格方便筛选引用；文稿方便粘贴转发。三种都会带引用编号，对应文末原评。</p>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button :disabled="busy" @click="download('md')">导出文稿</el-button>
        <el-button :disabled="busy" @click="download('xlsx')">导出表格</el-button>
        <el-button type="primary" :disabled="busy" @click="download('pdf')">导出 PDF</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" title="对应原评" size="480px">
      <div v-if="currentList.length" class="report-evidence-list">
        <div v-for="item in currentList" :key="item.id" class="report-evidence-item">
          <p class="muted">{{ platformLabel(item.platform) }} · {{ formatDateTime(item.reviewTime) }} · {{ sentimentLabel(item.sentiment) }}{{ item.aspectName ? ' · ' + item.aspectName : '' }}</p>
          <p class="report-quote">{{ item.content || item.quote || '—' }}</p>
        </div>
      </div>
      <p v-else class="muted">对应原评已不存在。</p>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { formatDateTime, formatMonth, friendlyMessage, platformLabel, sentimentLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const report = ref<any>(null)
const busy = ref(false)
const exportVisible = ref(false)
const drawer = ref(false)
const currentList = ref<any[]>([])
const sections = computed(() => (report.value?.sections || []).map((section: any) => ({
  ...section,
  followUp: competeFollowUp(section),
})))
const hasCompete = computed(() => sections.value.some((section: any) => section.code === 'compete'))

function competeFollowUp(section: { code?: string; claims?: { text?: string }[] }) {
  if (section.code !== 'compete') return ''
  const text = (section.claims || []).map((claim) => claim.text || '').join(' ')
  if (text.includes('还没填写竞品')) return '去项目填写竞品'
  if (text.includes('还没有对应条目')) return '去项目导入竞品评论'
  return ''
}

function aspectProducts(section: { aspectCompare?: { scores?: { name?: string }[] }[] }) {
  const first = section.aspectCompare?.[0]
  return (first?.scores || []).map((score) => score.name || '').filter(Boolean)
}

function aspectCell(row: { scores?: { name?: string; pos?: number; neg?: number }[] }, product: string) {
  const score = (row.scores || []).find((item) => item.name === product)
  if (!score) return '—'
  const pos = Number(score.pos) || 0
  const neg = Number(score.neg) || 0
  if (pos === 0 && neg === 0) return '—'
  return `正向 ${pos}，负向 ${neg}`
}

function monthProducts(section: { monthCompare?: { scores?: { name?: string }[] }[] }) {
  const first = section.monthCompare?.[0]
  return (first?.scores || []).map((score) => score.name || '').filter(Boolean)
}

function monthCell(
  row: { scores?: { name?: string; volume?: number; pos?: number; neg?: number }[] },
  product: string,
) {
  const score = (row.scores || []).find((item) => item.name === product)
  if (!score) return '—'
  const volume = Number(score.volume) || 0
  if (volume <= 0) return '—'
  return `原评 ${volume}，正向 ${Number(score.pos) || 0}，负向 ${Number(score.neg) || 0}`
}

async function load() {
  const { data } = await http.get(`/reports/${route.params.id}`)
  report.value = data.data
}

function evidenceOf(id: number) {
  return report.value?.evidences?.[id] || report.value?.evidences?.[String(id)] || null
}

function openClaim(claim: { evidenceIds?: number[] }) {
  currentList.value = (claim.evidenceIds || []).map((id) => evidenceOf(id)).filter(Boolean)
  drawer.value = true
}

async function download(format: 'xlsx' | 'md' | 'pdf') {
  if (!report.value?.id || busy.value) return
  busy.value = true
  try {
    const resp = await http.get(`/reports/${report.value.id}/export`, {
      params: { format },
      responseType: 'blob',
    })
    const blob = resp.data as Blob
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      try {
        const payload = JSON.parse(text)
        ElMessage.error(friendlyMessage(payload.message) || '导出失败')
      } catch {
        ElMessage.error('导出失败')
      }
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filenameFromHeader(resp.headers?.['content-disposition'], format)
    a.click()
    URL.revokeObjectURL(url)
    exportVisible.value = false
    ElMessage.success(format === 'xlsx' ? '已导出表格' : format === 'pdf' ? '已导出 PDF' : '已导出文稿')
  } finally {
    busy.value = false
  }
}

function filenameFromHeader(raw: unknown, format: 'xlsx' | 'md' | 'pdf') {
  const fallback = format === 'xlsx' ? '口碑报告.xlsx' : format === 'pdf' ? '口碑报告.pdf' : '口碑报告.md'
  const header = String(raw || '')
  const star = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (star) {
    try {
      return decodeURIComponent(star[1])
    } catch {
      return fallback
    }
  }
  const plain = header.match(/filename="?([^";]+)"?/i)
  return plain ? plain[1] : fallback
}

async function regenerate() {
  if (!report.value?.projectId || busy.value) return
  busy.value = true
  try {
    const { data } = await http.post(`/projects/${report.value.projectId}/reports`)
    ElMessage.success('已生成报告')
    await router.replace(`/app/reports/${data.data.id}`)
    await load()
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
