<template>
  <div v-if="report">
    <div class="page-head">
      <div>
        <h2>{{ report.title }}</h2>
        <p class="muted">{{ report.brand }} {{ report.mainModel }} · 每条结论都绑定了原评，点「查看原评」看对应原文。</p>
      </div>
      <div class="page-actions">
        <el-button v-permission="'report:generate'" :disabled="busy" @click="regenerate">生成报告</el-button>
        <el-button type="primary" @click="router.push(`/app/projects/${report.projectId}`)">去项目</el-button>
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

    <el-card v-for="section in report.sections || []" :key="section.code" class="block">
      <template #header>{{ section.title }}</template>
      <div v-for="(claim, idx) in section.claims || []" :key="idx" class="report-claim">
        <p>{{ claim.text }}</p>
        <div class="row-actions">
          <el-button v-if="(claim.evidenceIds || []).length" text @click="openClaim(claim)">查看原评</el-button>
        </div>
      </div>
    </el-card>

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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { formatDateTime, platformLabel, sentimentLabel } from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const report = ref<any>(null)
const busy = ref(false)
const drawer = ref(false)
const currentList = ref<any[]>([])

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
