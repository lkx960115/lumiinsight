<template>
  <div>
    <div class="page-head">
      <div>
        <h2>模型配置</h2>
        <p class="muted">分析按用途选择模型。关掉主模型后，只要备用可用，分析仍会走第二家。Key 加密保存，页面只显示后四位。</p>
      </div>
      <div class="page-actions">
        <el-button text @click="load">刷新</el-button>
        <el-button v-permission="'admin:llm:edit'" type="primary" @click="openProvider()">新增提供方</el-button>
      </div>
    </div>

    <section class="llm-section">
      <h3>分析用途</h3>
      <p class="muted">先定「这件事用哪家模型」。主模型关掉或没 Key 时，自动改用备用。</p>
      <div class="panel">
        <el-table :data="routes" empty-text="还没有分析用途。">
          <el-table-column label="用途" min-width="160">
            <template #default="{ row }">
              <div class="llm-purpose">{{ purposeName(row.purposeCode) }}</div>
              <div class="muted">{{ purposeHint(row.purposeCode) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="220">
            <template #default="{ row }">
              <div>{{ modelLabel(row.primaryModelId) }}</div>
              <p class="cell-note">{{ row.backupModelId ? '备用 ' + modelLabel(row.backupModelId) : '未设备用' }}</p>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="160">
            <template #default="{ row }">
              <span class="status-text" :class="routeHealth(row).ok ? 'is-ok' : 'is-bad'">{{ routeHealth(row).text }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button v-permission="'admin:llm:edit'" text @click="openRoute(row)">设置</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="llm-section">
      <h3>提供方</h3>
      <p class="muted">一家提供方下面可以挂多个模型。分析实际走的是上面用途里选中的那条。</p>
      <div v-if="!providers.length" class="panel muted" style="padding: 24px">还没有提供方，请先新增。</div>
      <div class="llm-grid">
        <article v-for="provider in providers" :key="provider.id" class="llm-card">
          <div class="llm-card-head">
            <div>
              <div class="llm-card-title">{{ provider.name }}</div>
              <div class="llm-url">{{ provider.baseUrl }}</div>
            </div>
            <div class="llm-card-tags">
              <el-switch
                v-model="provider.enabled"
                :active-value="1"
                :inactive-value="0"
                :disabled="!canEdit"
                @change="(val) => toggleProvider(provider, val)"
              />
              <el-tag size="small" :type="provider.hasKey ? 'success' : 'warning'">
                {{ provider.hasKey ? provider.apiKeyMasked : '未配置 Key' }}
              </el-tag>
            </div>
          </div>
          <div class="llm-models">
            <div v-if="!modelsOf(provider.id).length" class="muted">还没有模型</div>
            <div v-for="model in modelsOf(provider.id)" :key="model.id" class="llm-model-chip">
              <span>{{ model.modelCode }}</span>
              <el-switch
                v-model="model.enabled"
                :active-value="1"
                :inactive-value="0"
                :disabled="!canEdit"
                @change="(val) => toggleModel(model, val)"
              />
              <el-button v-permission="'admin:llm:edit'" text @click="openModel(model)">编辑</el-button>
            </div>
          </div>
          <div class="row-actions llm-card-actions">
            <el-button v-permission="'admin:llm:edit'" @click="openProvider(provider)">编辑提供方</el-button>
            <el-button v-permission="'admin:llm:edit'" @click="openModel(undefined, provider.id)">添加模型</el-button>
          </div>
        </article>
      </div>
    </section>

    <section class="llm-section">
      <h3>调用记录</h3>
      <p class="muted">只记 token 用量和成败，不记 Key 和原文。</p>
      <div class="panel">
        <el-table :data="usageRows" empty-text="还没有调用记录。">
          <el-table-column label="用途" min-width="120">
            <template #default="{ row }">{{ purposeName(row.purposeCode) }}</template>
          </el-table-column>
          <el-table-column label="模型" min-width="160">
            <template #default="{ row }">{{ row.modelCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" min-width="200">
            <template #default="{ row }">
              <span class="status-text" :class="row.success === 1 ? 'is-ok' : 'is-bad'">
                {{ row.success === 1 ? '成功' : '失败' }}
              </span>
              <p v-if="row.detail" class="cell-note">{{ friendlyMessage(row.detail) }}</p>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="168">
            <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog v-model="pVisible" :title="pForm.id ? '编辑提供方' : '新增提供方'" width="560px">
      <el-form label-width="108px">
        <el-form-item label="名称">
          <el-input v-model="pForm.name" placeholder="如：中转网关" />
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="pForm.baseUrl" placeholder="https://api.example.com/v1" />
          <div class="muted">填到 /v1 这一层，系统会请求 /chat/completions</div>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="pForm.apiKey" type="password" show-password :placeholder="pForm.id ? '留空则不改已保存的 Key' : '不会在页面回显明文'" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="pForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mVisible" :title="mForm.id ? '编辑模型' : '添加模型'" width="520px">
      <el-form label-width="108px">
        <el-form-item label="提供方">
          <el-select v-model="mForm.providerId" style="width: 100%" placeholder="选择提供方">
            <el-option v-for="item in providers" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码">
          <el-input v-model="mForm.modelCode" placeholder="网关要求的模型名，如 gpt-5.5" />
        </el-form-item>
        <el-form-item label="JSON 模式">
          <el-switch v-model="mForm.jsonMode" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="mForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mVisible = false">取消</el-button>
        <el-button type="primary" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rVisible" title="设置用途模型" width="560px">
      <el-form label-width="108px">
        <el-form-item label="用途">
          <div>
            <div>{{ purposeName(rForm.purposeCode) }}</div>
            <div class="muted">{{ purposeHint(rForm.purposeCode) }}</div>
          </div>
        </el-form-item>
        <el-form-item label="主模型">
          <el-select v-model="rForm.primaryModelId" style="width: 100%" placeholder="选择主模型" filterable>
            <el-option-group v-for="provider in providers" :key="provider.id" :label="provider.name">
              <el-option
                v-for="model in modelsOf(provider.id)"
                :key="model.id"
                :label="model.modelCode + (model.enabled === 1 ? '' : '（停用）')"
                :value="model.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="备用模型">
          <el-select v-model="rForm.backupModelId" style="width: 100%" placeholder="可选" clearable filterable>
            <el-option-group v-for="provider in providers" :key="provider.id" :label="provider.name">
              <el-option
                v-for="model in modelsOf(provider.id)"
                :key="model.id"
                :label="model.modelCode + (model.enabled === 1 ? '' : '（停用）')"
                :value="model.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatClock, friendlyMessage } from '@/utils/labels'

const auth = useAuthStore()
const canEdit = computed(() => auth.has('admin:llm:edit'))

const PURPOSE: Record<string, { name: string; hint: string }> = {
  absa: { name: '方面情感', hint: '把评论拆成方面、正负向和原因' },
  sentiment: { name: '整句情感', hint: '整条评论的正负向' },
  report_summary: { name: '报告摘要', hint: '基于统计和原声写摘要，不能空编' },
  embedding: { name: '向量检索', hint: '第三周按证据找回原评' },
  spam_classify: { name: '去水辅助', hint: '可选；清洗仍以规则为主' },
}

const providers = ref<any[]>([])
const models = ref<any[]>([])
const routes = ref<any[]>([])
const usageRows = ref<any[]>([])
const pVisible = ref(false)
const mVisible = ref(false)
const rVisible = ref(false)
const pForm = reactive<any>({ id: null, name: '', baseUrl: '', apiKey: '', enabled: 1, protocol: 'openai_compat' })
const mForm = reactive<any>({ id: null, providerId: null, modelCode: '', jsonMode: 1, enabled: 1 })
const rForm = reactive<any>({ id: null, purposeCode: '', primaryModelId: null, backupModelId: null, enabled: 1 })

async function load() {
  const [p, m, r, u] = await Promise.all([
    http.get('/admin/llm/providers'),
    http.get('/admin/llm/models'),
    http.get('/admin/llm/routes'),
    http.get('/admin/llm/usage'),
  ])
  providers.value = p.data.data
  models.value = m.data.data
  routes.value = r.data.data
  usageRows.value = u.data.data || []
}

function modelsOf(providerId: number) {
  return models.value.filter((item) => item.providerId === providerId)
}

function purposeName(code: string) {
  return PURPOSE[code]?.name || code
}

function purposeHint(code: string) {
  return PURPOSE[code]?.hint || ''
}

function providerOfModel(modelId?: number | null) {
  const model = models.value.find((item) => item.id === modelId)
  if (!model) return null
  return providers.value.find((item) => item.id === model.providerId) || null
}

function modelOf(modelId?: number | null) {
  return models.value.find((item) => item.id === modelId) || null
}

function modelLabel(modelId?: number | null) {
  const model = modelOf(modelId)
  if (!model) return '未设置'
  const provider = providerOfModel(modelId)
  return `${provider?.name || '未知提供方'} · ${model.modelCode}`
}

function modelUsable(modelId?: number | null) {
  const model = modelOf(modelId)
  const provider = providerOfModel(modelId)
  return Boolean(model && model.enabled === 1 && provider && provider.enabled === 1 && provider.hasKey)
}

function routeHealth(row: any) {
  if (modelUsable(row.primaryModelId)) return { ok: true, text: '可用' }
  if (modelUsable(row.backupModelId)) return { ok: true, text: '将用备用' }
  if (!modelOf(row.primaryModelId)) return { ok: false, text: '未设置主模型' }
  const provider = providerOfModel(row.primaryModelId)
  if (modelOf(row.primaryModelId)?.enabled !== 1) return { ok: false, text: '主模型已停用' }
  if (!provider || provider.enabled !== 1) return { ok: false, text: '提供方已停用' }
  if (!provider.hasKey) return { ok: false, text: '未配置 Key，将走词典规则' }
  return { ok: false, text: '将走词典规则' }
}

function openProvider(row?: any) {
  if (row) Object.assign(pForm, { id: row.id, name: row.name, baseUrl: row.baseUrl, apiKey: '', enabled: row.enabled, protocol: 'openai_compat' })
  else Object.assign(pForm, { id: null, name: '', baseUrl: '', apiKey: '', enabled: 1, protocol: 'openai_compat' })
  pVisible.value = true
}

function openModel(row?: any, providerId?: number) {
  if (row) {
    Object.assign(mForm, {
      id: row.id,
      providerId: row.providerId,
      modelCode: row.modelCode,
      jsonMode: row.jsonMode ?? 1,
      enabled: row.enabled,
    })
  } else {
    Object.assign(mForm, {
      id: null,
      providerId: providerId || providers.value[0]?.id || null,
      modelCode: '',
      jsonMode: 1,
      enabled: 1,
    })
  }
  mVisible.value = true
}

function openRoute(row: any) {
  Object.assign(rForm, {
    id: row.id,
    purposeCode: row.purposeCode,
    primaryModelId: row.primaryModelId,
    backupModelId: row.backupModelId,
    enabled: row.enabled,
  })
  rVisible.value = true
}

async function toggleProvider(provider: any, enabled: number) {
  try {
    await http.put(`/admin/llm/providers/${provider.id}`, {
      name: provider.name,
      protocol: provider.protocol || 'openai_compat',
      baseUrl: provider.baseUrl,
      enabled,
      timeoutMs: provider.timeoutMs || 60000,
      maxRetry: provider.maxRetry ?? 1,
    })
    ElMessage.success(enabled === 1 ? '已启用' : '已停用')
  } catch {
    provider.enabled = enabled === 1 ? 0 : 1
  }
}

async function toggleModel(model: any, enabled: number) {
  try {
    await http.put(`/admin/llm/models/${model.id}`, {
      providerId: model.providerId,
      modelCode: model.modelCode,
      jsonMode: model.jsonMode ?? 1,
      contextLength: model.contextLength,
      inputPrice: model.inputPrice,
      outputPrice: model.outputPrice,
      enabled,
    })
    ElMessage.success(enabled === 1 ? '已启用' : '已停用')
  } catch {
    model.enabled = enabled === 1 ? 0 : 1
  }
}

async function saveProvider() {
  if (!String(pForm.name || '').trim() || !String(pForm.baseUrl || '').trim()) {
    ElMessage.warning('请填写名称和接口地址')
    return
  }
  const payload: any = {
    name: pForm.name,
    protocol: 'openai_compat',
    baseUrl: pForm.baseUrl,
    enabled: pForm.enabled,
    timeoutMs: 60000,
    maxRetry: 1,
  }
  if (pForm.apiKey) payload.apiKey = pForm.apiKey
  if (pForm.id) await http.put(`/admin/llm/providers/${pForm.id}`, payload)
  else await http.post('/admin/llm/providers', payload)
  ElMessage.success('提供方已保存')
  pVisible.value = false
  await load()
}

async function saveModel() {
  if (!mForm.providerId || !String(mForm.modelCode || '').trim()) {
    ElMessage.warning('请选择提供方并填写模型编码')
    return
  }
  const payload = {
    providerId: mForm.providerId,
    modelCode: mForm.modelCode,
    jsonMode: mForm.jsonMode,
    enabled: mForm.enabled,
  }
  if (mForm.id) await http.put(`/admin/llm/models/${mForm.id}`, payload)
  else await http.post('/admin/llm/models', payload)
  ElMessage.success('模型已保存')
  mVisible.value = false
  await load()
}

async function saveRoute() {
  await http.put(`/admin/llm/routes/${rForm.id}`, {
    purposeCode: rForm.purposeCode,
    primaryModelId: rForm.primaryModelId,
    backupModelId: rForm.backupModelId || null,
    enabled: rForm.enabled ?? 1,
  })
  ElMessage.success('用途已更新，下一轮分析生效')
  rVisible.value = false
  await load()
}

onMounted(load)
</script>
