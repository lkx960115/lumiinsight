export const PLATFORM_LABEL: Record<string, string> = {
  xiaohongshu: '小红书',
  jd: '京东',
  taobao: '淘宝',
  douyin: '抖音',
}

export function platformLabel(code?: string | null) {
  if (!code) return '—'
  return PLATFORM_LABEL[code] || code
}

export function platformListLabel(codes?: string[] | null) {
  if (!codes?.length) return '—'
  return codes.map((code) => platformLabel(code)).join('、')
}

export function jobTypeLabel(type?: string | null) {
  if (type === 'CLEAN') return '清洗'
  if (type === 'ANALYZE') return '分析'
  if (type === 'RUN') return '清洗并分析'
  return type || '—'
}

export function jobStatusLabel(status?: string | null) {
  const map: Record<string, string> = {
    PENDING: '排队中',
    IMPORTING: '导入中',
    CLEANING: '清洗中',
    ANALYZING: '分析中',
    INDEXING: '建立检索',
    REPORTING: '生成报告',
    READY: '完成',
    FAILED: '失败',
  }
  return map[status || ''] || status || '—'
}

export function formatDateTime(value?: string | null) {
  if (!value) return '—'
  const raw = String(value).replace('T', ' ').slice(0, 19)
  if (raw.endsWith(' 00:00:00')) return raw.slice(0, 10)
  return raw
}

export function purposeLabel(code?: string | null) {
  const map: Record<string, string> = {
    absa: '方面情感',
    sentiment: '整句情感',
    report_summary: '报告摘要',
    embedding: '向量检索',
    spam_classify: '去水辅助',
  }
  if (!code) return ''
  return map[code] || code
}

export function auditActionLabel(action?: string | null) {
  const map: Record<string, string> = {
    'pipeline.clean': '清洗评论',
    'pipeline.analyze': '分析评论',
    'pipeline.run': '清洗并分析',
    'pipeline.retry': '重试分析',
    'llm.provider.save': '保存模型提供方',
    'llm.model.save': '保存模型',
    'llm.route.save': '设置分析用途',
    'import.submit': '导入评论',
    'project.create': '新建项目',
    'project.update': '更新项目',
    'project.delete': '删除项目',
    'dict.save': '更新方面词典',
    'user.create': '新建用户',
    'user.update': '更新用户',
  }
  if (!action) return '—'
  return map[action] || action
}

export function auditDetailLabel(action?: string | null, detail?: string | null) {
  const text = String(detail || '').trim()
  if (!text || /^\d+$/.test(text)) return ''
  if (action === 'llm.route.save') return purposeLabel(text)
  return text.replace(/^例如/, '').replace(/[「『」』]/g, '').trim()
}

export function formatClock(value?: string | null) {
  if (!value) return '—'
  const raw = String(value).trim()
  const iso = raw.includes('T') ? raw : raw.replace(' ', 'T')
  const dated = /[zZ]|[+-]\d{2}:\d{2}$/.test(raw) ? new Date(raw) : new Date(`${iso}Z`)
  if (Number.isNaN(dated.getTime())) return formatDateTime(value)
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(dated)
}

export function isRunningStatus(status?: string | null) {
  return ['PENDING', 'IMPORTING', 'CLEANING', 'ANALYZING', 'INDEXING', 'REPORTING'].includes(String(status || ''))
}

export function friendlyMessage(raw?: string | null) {
  const text = String(raw || '').trim()
  if (!text) return '—'
  const lower = text.toLowerCase()
  if (/octet-stream|httpmessageconverter|extracting response|java\.util\.map/.test(lower)) {
    return '分析服务没有返回可用结果，请重试'
  }
  if (/worker 不可用|connection refused|connect timed out/.test(lower)) {
    return '分析服务暂时连不上，请稍后重试'
  }
  if (lower.includes('timeout') || lower.includes('timed out') || text.includes('超时')) {
    return '处理超时，请稍后重试'
  }
  if (/exception|caused by:|at com\.|java\./i.test(text)) {
    return '处理失败，请重试'
  }
  return text
}

export function importRemark(row: { successRows?: number; failRows?: number; status?: string; message?: string }) {
  const ok = Number(row.successRows) || 0
  const fail = Number(row.failRows) || 0
  if (row.status === 'FAILED') return friendlyMessage(row.message)
  if (fail > 0) return `${ok} 条成功，${fail} 条失败`
  if (ok > 0) return `${ok} 条`
  return friendlyMessage(row.message)
}

export function pipelineRemark(message?: string | null) {
  const raw = String(message || '').trim()
  if (!raw) return '—'
  const analyzed = raw.match(/分析完成：(\d+) 条，方面 (\d+) 条(?:（([^）]*)）)?/)
  if (analyzed) {
    const how = analyzed[3] || ''
    if (/未配置模型 Key/.test(how)) return `${analyzed[1]} 条，${analyzed[2]} 个方面（未配置模型，按词典）`
    if (/调用失败已降级/.test(how)) return `${analyzed[1]} 条，${analyzed[2]} 个方面（模型失败，已按词典）`
    if (/未返回有效结果/.test(how)) return `${analyzed[1]} 条，${analyzed[2]} 个方面（模型无结果，已按词典）`
    return `${analyzed[1]} 条，${analyzed[2]} 个方面`
  }
  const cleaned = raw.match(/清洗完成：有效 (\d+)\/(\d+)，去重 (\d+)，广告 (\d+)，过短 (\d+)/)
  if (cleaned) {
    const bits = [`有效 ${cleaned[1]} / ${cleaned[2]} 条`]
    if (Number(cleaned[3]) > 0) bits.push(`去重 ${cleaned[3]}`)
    if (Number(cleaned[4]) > 0) bits.push(`广告 ${cleaned[4]}`)
    if (Number(cleaned[5]) > 0) bits.push(`过短 ${cleaned[5]}`)
    return bits.join('，')
  }
  return friendlyMessage(raw)
}
