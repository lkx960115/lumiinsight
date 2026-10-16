<template>
  <div v-if="visible" class="ask-dock">
    <button v-show="!open" class="ask-rail" type="button" @click="open = true">问原评</button>
    <aside v-if="open" class="ask-panel" role="dialog" aria-labelledby="ask-title">
      <header class="ask-head">
        <div>
          <h3 id="ask-title">问原评</h3>
          <p>只根据本项目已分析的原评回答，没有依据就不编。</p>
        </div>
        <button class="ask-close" type="button" aria-label="收起" @click="open = false">收起</button>
      </header>
      <div class="ask-presets">
        <button
          v-for="item in presets"
          :key="item"
          class="ask-chip"
          type="button"
          :disabled="busy"
          @click="ask(item)"
        >
          {{ item }}
        </button>
      </div>
      <div ref="threadEl" class="ask-thread">
        <p v-if="!messages.length" class="ask-empty">点上面的现成问法，或自己问安装、色温、售后、价格。回答下面可以核对原文。</p>
        <article v-for="(item, idx) in messages" :key="idx" class="ask-turn" :class="'is-' + item.role">
          <p class="ask-bubble">{{ item.text }}</p>
          <div v-if="item.role === 'assistant' && item.evidences?.length" class="ask-evidence">
            <button class="ask-evidence-toggle" type="button" @click="item.showEvidence = !item.showEvidence">
              {{ item.showEvidence ? '收起原评' : '查看原评' }}
            </button>
            <div v-if="item.showEvidence" class="ask-quotes">
              <blockquote v-for="row in item.evidences" :key="row.id">
                <cite>{{ platformLabel(row.platform) }} · {{ formatDateTime(row.reviewTime) }} · {{ sentimentLabel(row.sentiment) }}{{ row.aspectName ? ' · ' + row.aspectName : '' }}</cite>
                <p>{{ row.content || row.quote || '—' }}</p>
              </blockquote>
            </div>
          </div>
        </article>
        <p v-if="busy" class="ask-wait">正在对照原评…</p>
      </div>
      <form class="ask-composer" @submit.prevent="ask(draft)">
        <textarea
          v-model="draft"
          rows="2"
          maxlength="200"
          placeholder="例如：安装最常被吐槽的是什么？"
          :disabled="busy"
          @keydown.enter.exact.prevent="ask(draft)"
        />
        <button class="ask-send" type="submit" :disabled="busy || !draft.trim()">提问</button>
      </form>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/api/http'
import { formatDateTime, friendlyMessage, platformLabel, sentimentLabel } from '@/utils/labels'

const presets = [
  '安装最常被吐槽的是什么？',
  '色温偏冷还是偏暖？',
  '售后负向主要集中在哪？',
  '价格贵不贵，原评怎么说？',
  '物流和包装有没有问题？',
  '竞品被抱怨最多的是哪方面？',
]

type Evidence = {
  id: number
  platform?: string
  reviewTime?: string
  sentiment?: string
  aspectName?: string
  content?: string
  quote?: string
}

type Turn = {
  role: 'user' | 'assistant'
  text: string
  evidences?: Evidence[]
  showEvidence?: boolean
}

const route = useRoute()
const open = ref(false)
const busy = ref(false)
const draft = ref('')
const projectId = ref<number | null>(null)
const messages = ref<Turn[]>([])
const threadEl = ref<HTMLElement | null>(null)
const visible = computed(() => projectId.value != null)

watch(
  () => [route.path, route.params.id],
  resolveProject,
  { immediate: true },
)

watch(projectId, (next, prev) => {
  if (next !== prev) {
    messages.value = []
    draft.value = ''
  }
})

async function resolveProject() {
  const projectMatch = route.path.match(/^\/app\/projects\/(\d+)$/)
  if (projectMatch) {
    projectId.value = Number(projectMatch[1])
    return
  }
  const reportMatch = route.path.match(/^\/app\/reports\/(\d+)$/)
  if (reportMatch) {
    try {
      const { data } = await http.get(`/reports/${reportMatch[1]}`, { silent: true } as any)
      projectId.value = data.data?.projectId || null
    } catch {
      projectId.value = null
      open.value = false
    }
    return
  }
  projectId.value = null
  open.value = false
}

async function ask(raw: string) {
  const question = String(raw || '').trim()
  if (!question || !projectId.value || busy.value) return
  draft.value = ''
  messages.value.push({ role: 'user', text: question })
  open.value = true
  busy.value = true
  await scrollThread()
  try {
    const { data } = await http.post(
      `/projects/${projectId.value}/ask`,
      { question },
      { timeout: 120000, silent: true } as any,
    )
    const row = data.data || {}
    messages.value.push({
      role: 'assistant',
      text: row.answer || '这批评论里还没有足够原评来回答。',
      evidences: row.evidences || [],
      showEvidence: false,
    })
  } catch (err: any) {
    const payload = err?.response?.data || err
    messages.value.push({
      role: 'assistant',
      text: friendlyMessage(payload?.message || err?.message) || '暂时问不了，请稍后重试。',
      evidences: [],
    })
  } finally {
    busy.value = false
    await scrollThread()
  }
}

async function scrollThread() {
  await nextTick()
  if (threadEl.value) {
    threadEl.value.scrollTop = threadEl.value.scrollHeight
  }
}
</script>
