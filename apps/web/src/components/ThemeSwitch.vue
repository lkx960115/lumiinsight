<template>
  <div class="theme-switch" :class="`is-${variant}`" role="group" aria-label="切换界面主题">
    <button
      v-for="(item, index) in THEME_OPTIONS"
      :key="item.key"
      type="button"
      class="theme-opt"
      :class="{ 'is-on': theme.current === item.key }"
      :title="`${item.name} · ${item.hint}（快捷键 ${index + 1}）`"
      :aria-pressed="theme.current === item.key"
      @click="theme.set(item.key)"
    >
      <span class="theme-dot" :style="{ background: item.dot }" />
      <span class="theme-name">{{ item.name }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { THEME_OPTIONS, isThemeKey, useThemeStore } from '@/stores/theme'

withDefaults(defineProps<{ variant?: 'inline' | 'floating' }>(), { variant: 'inline' })

const theme = useThemeStore()

function isTyping(target: EventTarget | null) {
  const el = target as HTMLElement | null
  if (!el) return false
  return Boolean(el.closest('input, textarea, select, [contenteditable="true"]'))
}

/** 1 / 2 / 3 切主题；输入框里按数字不抢键 */
function onKeydown(event: KeyboardEvent) {
  if (event.metaKey || event.ctrlKey || event.altKey) return
  if (isTyping(event.target)) return
  const map: Record<string, string> = { '1': 'a', '2': 'b', '3': 'c' }
  const key = map[event.key]
  if (key && isThemeKey(key)) theme.set(key)
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>
