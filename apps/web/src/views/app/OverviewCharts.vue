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
    <p v-if="!hasSentiment" class="muted">还没有分析结果。导入后点「清洗并分析」。</p>
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
    <div v-if="hasSentiment" class="overview-trend">
      <h3>按月情感</h3>
      <p v-if="!hasMonths" class="muted">评论没有时间，无法按月看走势。</p>
      <div v-show="hasMonths" ref="trendEl" class="overview-chart" />
    </div>
    <p v-if="error" class="muted">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useThemeStore } from '@/stores/theme'

type AspectItem = { name: string; total: number; pos: number; neg: number; neu: number }
type MonthItem = { label: string; pos: number; neg: number; neu: number }
type Overview = {
  imported: number
  counted: number
  analyzed: number
  pos: number
  neg: number
  neu: number
  aspects?: AspectItem[]
  months?: MonthItem[]
}

const props = defineProps<{ overview: Overview | null }>()
const emit = defineEmits<{ 'select-aspect': [name: string] }>()

const pieEl = ref<HTMLElement | null>(null)
const barEl = ref<HTMLElement | null>(null)
const trendEl = ref<HTMLElement | null>(null)
const error = ref('')
let pieChart: EChartsLike | null = null
let barChart: EChartsLike | null = null
let trendChart: EChartsLike | null = null

const theme = useThemeStore()

/** 图表颜色跟着当前主题走，不写死色值 */
function token(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function palette() {
  return {
    pos: token('--pos', '#1f6a56'),
    neg: token('--neg', '#b0402f'),
    neu: token('--ink-soft', '#8a9a94'),
    surface: token('--surface', '#fdfbf6'),
    ink: token('--ink', '#1b2a25'),
    soft: token('--ink-soft', '#6f7c74'),
    line: token('--line', 'rgba(27,42,37,.14)'),
  }
}

const hasSentiment = computed(() => {
  const o = props.overview
  return Boolean(o && o.pos + o.neg + o.neu > 0)
})
const hasAspects = computed(() => (props.overview?.aspects || []).some((item) => item.total > 0))
const months = computed(() => (props.overview?.months || []).filter((item) => item.pos + item.neg + item.neu > 0))
const hasMonths = computed(() => months.value.length > 0)

type EChartsLike = {
  setOption: (opt: unknown, notMerge?: boolean) => void
  resize: () => void
  dispose: () => void
  on: (event: string, handler: (params: { name?: string }) => void) => void
  off: (event: string) => void
}

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
    trendChart?.dispose()
    pieChart = null
    barChart = null
    trendChart = null
    return
  }
  const c = palette()
  const fontBody = token('--font-body', 'sans-serif')
  if (pieEl.value) {
    pieChart = pieChart || api.init(pieEl.value)
    pieChart.setOption({
      color: [c.pos, c.neg, c.neu],
      textStyle: { color: c.ink, fontFamily: fontBody },
      tooltip: { trigger: 'item', formatter: '{b} {c} 条（{d}%）' },
      legend: { bottom: 0, textStyle: { color: c.soft } },
      series: [{
        type: 'pie',
        radius: ['44%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: c.surface, borderWidth: 2 },
        label: { formatter: '{b}\n{d}%', color: c.ink },
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
    barChart.off('click')
    barChart.on('click', (params) => {
      if (params.name) emit('select-aspect', params.name)
    })
    const names = aspects.map((item) => item.name).reverse()
    barChart.setOption({
      textStyle: { color: c.ink, fontFamily: fontBody },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, textStyle: { color: c.soft } },
      grid: { left: 88, right: 16, top: 8, bottom: 36 },
      yAxis: {
        type: 'category',
        data: names,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: c.line } },
        axisLabel: { color: c.soft },
      },
      xAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: c.soft },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        { name: '正向', type: 'bar', stack: 'aspect', barWidth: 14, data: aspects.map((item) => item.pos).reverse(), itemStyle: { color: c.pos } },
        { name: '负向', type: 'bar', stack: 'aspect', data: aspects.map((item) => item.neg).reverse(), itemStyle: { color: c.neg } },
        { name: '中性', type: 'bar', stack: 'aspect', data: aspects.map((item) => item.neu).reverse(), itemStyle: { color: c.neu } },
      ],
    }, true)
  } else if (barChart) {
    barChart.dispose()
    barChart = null
  }
  if (trendEl.value && hasMonths.value) {
    trendChart = trendChart || api.init(trendEl.value)
    const labels = months.value.map((item) => monthLabel(item.label))
    trendChart.setOption({
      textStyle: { color: c.ink, fontFamily: fontBody },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, textStyle: { color: c.soft } },
      grid: { left: 48, right: 16, top: 8, bottom: 36 },
      xAxis: {
        type: 'category',
        data: labels,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: c.line } },
        axisLabel: { color: c.soft },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: c.soft },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        { name: '正向', type: 'bar', stack: 'month', barWidth: 18, data: months.value.map((item) => item.pos), itemStyle: { color: c.pos } },
        { name: '负向', type: 'bar', stack: 'month', data: months.value.map((item) => item.neg), itemStyle: { color: c.neg } },
        { name: '中性', type: 'bar', stack: 'month', data: months.value.map((item) => item.neu), itemStyle: { color: c.neu } },
      ],
    }, true)
  } else if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
}

function monthLabel(raw: string) {
  const match = String(raw || '').match(/^(\d{4})-(\d{2})$/)
  if (!match) return raw
  return `${match[1]}年${Number(match[2])}月`
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
  trendChart?.resize()
}

watch(() => props.overview, () => refresh(), { deep: true })
// 换主题后重画，图表颜色与文字跟着 token 变
watch(() => theme.current, async () => {
  await nextTick()
  if (echartsApi() && pieChart) render()
})
onMounted(() => {
  refresh()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
  trendChart?.dispose()
})
</script>
