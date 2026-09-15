<template>
  <div class="overview-charts">
    <div class="overview-stats">
      <div class="overview-stat">
        <strong>{{ overview?.imported ?? 0 }}</strong>
        <span>已导入</span>
      </div>
      <div class="overview-stat">
        <strong>{{ overview?.counted ?? 0 }}</strong>
        <span>有效评论</span>
      </div>
      <div class="overview-stat">
        <strong>{{ overview?.analyzed ?? 0 }}</strong>
        <span>已分析</span>
      </div>
    </div>
    <p v-if="!hasSentiment" class="muted">还没有分析结果。导入后点「清洗并分析」，图表会按有效评论统计。</p>
    <div v-else class="overview-grid">
      <div>
        <h3>情感分布</h3>
        <div ref="pieEl" class="overview-chart" />
      </div>
      <div>
        <h3>方面分布</h3>
        <p v-if="!hasAspects" class="muted">有效评论里还没有方面命中。</p>
        <div v-show="hasAspects" ref="barEl" class="overview-chart" />
      </div>
    </div>
    <p v-if="error" class="muted">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type AspectItem = { name: string; total: number; pos: number; neg: number; neu: number }
type Overview = {
  imported: number
  counted: number
  analyzed: number
  pos: number
  neg: number
  neu: number
  aspects?: AspectItem[]
}

const props = defineProps<{ overview: Overview | null }>()

const pieEl = ref<HTMLElement | null>(null)
const barEl = ref<HTMLElement | null>(null)
const error = ref('')
let pieChart: EChartsLike | null = null
let barChart: EChartsLike | null = null

const COLORS = { pos: '#1f6a56', neg: '#b4533a', neu: '#8a9a94' }

const hasSentiment = computed(() => {
  const o = props.overview
  return Boolean(o && o.pos + o.neg + o.neu > 0)
})
const hasAspects = computed(() => (props.overview?.aspects || []).some((item) => item.total > 0))

type EChartsLike = { setOption: (opt: unknown, notMerge?: boolean) => void; resize: () => void; dispose: () => void }

function echartsApi() {
  return (window as Window & { echarts?: { init: (el: HTMLElement) => EChartsLike } }).echarts
}

function loadScript(src: string) {
  return new Promise<void>((resolve, reject) => {
    if (echartsApi()) {
      resolve()
      return
    }
    const found = document.querySelector(`script[data-lumi-echarts]`) as HTMLScriptElement | null
    if (found) {
      found.addEventListener('load', () => resolve())
      found.addEventListener('error', () => reject(new Error('图表库加载失败')))
      return
    }
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.dataset.lumiEcharts = '1'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('图表库加载失败'))
    document.head.appendChild(script)
  })
}

async function ensureEcharts() {
  if (echartsApi()) {
    return echartsApi()
  }
  const sources = [
    '/vendor/echarts.min.js',
    'https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js',
  ]
  let last: Error | null = null
  for (const src of sources) {
    try {
      await loadScript(src)
      if (echartsApi()) {
        return echartsApi()
      }
    } catch (e) {
      last = e instanceof Error ? e : new Error('图表库加载失败')
      document.querySelector('script[data-lumi-echarts]')?.remove()
    }
  }
  throw last || new Error('图表库加载失败')
}

function render() {
  const api = echartsApi()
  const overview = props.overview
  if (!api || !overview || !hasSentiment.value) {
    pieChart?.dispose()
    barChart?.dispose()
    pieChart = null
    barChart = null
    return
  }
  if (pieEl.value) {
    pieChart = pieChart || api.init(pieEl.value)
    pieChart.setOption({
      color: [COLORS.pos, COLORS.neg, COLORS.neu],
      tooltip: { trigger: 'item', formatter: '{b} {c} 条（{d}%）' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['44%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fbf8f2', borderWidth: 2 },
        label: { formatter: '{b}\n{d}%' },
        data: [
          { name: '正向', value: overview.pos },
          { name: '负向', value: overview.neg },
          { name: '中性', value: overview.neu },
        ].filter((item) => item.value > 0),
      }],
    }, true)
  }
  const aspects = (overview.aspects || []).filter((item) => item.total > 0).slice(0, 8)
  if (barEl.value && aspects.length) {
    barChart = barChart || api.init(barEl.value)
    const names = aspects.map((item) => item.name).reverse()
    barChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0 },
      grid: { left: 88, right: 16, top: 8, bottom: 36 },
      yAxis: { type: 'category', data: names, axisTick: { show: false } },
      xAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '正向', type: 'bar', stack: 'aspect', barWidth: 14, data: aspects.map((item) => item.pos).reverse(), itemStyle: { color: COLORS.pos } },
        { name: '负向', type: 'bar', stack: 'aspect', data: aspects.map((item) => item.neg).reverse(), itemStyle: { color: COLORS.neg } },
        { name: '中性', type: 'bar', stack: 'aspect', data: aspects.map((item) => item.neu).reverse(), itemStyle: { color: COLORS.neu } },
      ],
    }, true)
  } else if (barChart) {
    barChart.dispose()
    barChart = null
  }
}

async function refresh() {
  error.value = ''
  try {
    await ensureEcharts()
    await nextTick()
    render()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '图表库加载失败'
  }
}

function onResize() {
  pieChart?.resize()
  barChart?.resize()
}

watch(() => props.overview, () => refresh(), { deep: true })
onMounted(() => {
  refresh()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>
