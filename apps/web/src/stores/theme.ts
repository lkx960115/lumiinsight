import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeKey = 'a' | 'b' | 'c'

export type ThemeOption = {
  key: ThemeKey
  name: string
  hint: string
  dot: string
}

/** 三套界面方向，和 design/ui-directions 一一对应 */
export const THEME_OPTIONS: ThemeOption[] = [
  { key: 'a', name: '静数据', hint: '白底蓝调，信息密度高，适合白天连着看表', dot: 'linear-gradient(135deg,#2b57d6,#eef3ff)' },
  { key: 'b', name: '纸鉴', hint: '纸色墨绿，宋体标题，最像一份可交付的报告', dot: 'linear-gradient(135deg,#1f6a56,#efe9dd)' },
  { key: 'c', name: '暗房', hint: '深色暖橙，夜里长时间看数不刺眼', dot: 'linear-gradient(135deg,#f0a35e,#181d22)' },
]

const STORAGE_KEY = 'lumi-theme'
const DEFAULT_THEME: ThemeKey = 'b'

export function isThemeKey(value: unknown): value is ThemeKey {
  return value === 'a' || value === 'b' || value === 'c'
}

export function readStoredTheme(): ThemeKey {
  // 演示/分享链接可用 ?theme=a|b|c 直接指定，优先于本地记忆
  try {
    const fromUrl = new URLSearchParams(location.search).get('theme')
    if (isThemeKey(fromUrl)) {
      localStorage.setItem(STORAGE_KEY, fromUrl)
      return fromUrl
    }
  } catch {
    /* 取不到 search 就退回本地记忆 */
  }
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (isThemeKey(saved)) return saved
  } catch {
    /* 隐私模式下读不到 localStorage，用默认 */
  }
  return DEFAULT_THEME
}

/** 把主题写到 <html data-theme>，同时告诉浏览器 明/暗，滚动条与原生控件跟着变 */
export function applyTheme(theme: ThemeKey) {
  const root = document.documentElement
  root.dataset.theme = theme
  root.style.colorScheme = theme === 'c' ? 'dark' : 'light'
}

export function persistTheme(theme: ThemeKey) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* 存不下就只在本次会话生效 */
  }
}

export const useThemeStore = defineStore('theme', () => {
  const current = ref<ThemeKey>(readStoredTheme())

  function set(theme: ThemeKey) {
    current.value = isThemeKey(theme) ? theme : DEFAULT_THEME
    applyTheme(current.value)
    persistTheme(current.value)
  }

  /** 键盘 1/2/3 顺序切换 */
  function step(offset: number) {
    const index = THEME_OPTIONS.findIndex((item) => item.key === current.value)
    const next = THEME_OPTIONS[(index + offset + THEME_OPTIONS.length) % THEME_OPTIONS.length]
    set(next.key)
  }

  return { current, set, step }
})
