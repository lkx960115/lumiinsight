<template>
  <div>
    <div class="page-head">
      <div>
        <h2>模型提供方与路由</h2>
        <p class="muted">Key 加密保存，列表只显示后四位。调用必须走用途路由，禁止写死单一厂商。</p>
      </div>
      <el-button v-permission="'admin:llm:edit'" type="primary" @click="openProvider()">新增提供方</el-button>
    </div>

    <el-table :data="providers">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="protocol" label="协议" width="140" />
      <el-table-column prop="baseUrl" label="Base URL" />
      <el-table-column prop="apiKeyMasked" label="Key" width="140" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-permission="'admin:llm:edit'" text @click="openProvider(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3>模型</h3>
    <el-button v-permission="'admin:llm:edit'" @click="openModel()">新增模型</el-button>
    <el-table :data="models" class="mt">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="providerId" label="提供方ID" width="100" />
      <el-table-column prop="modelCode" label="模型编码" />
      <el-table-column prop="enabled" label="启用" width="80" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-permission="'admin:llm:edit'" text @click="openModel(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3>用途路由（主 + 备）</h3>
    <el-table :data="routes">
      <el-table-column prop="purposeCode" label="用途" />
      <el-table-column prop="primaryModelId" label="主模型ID" />
      <el-table-column prop="backupModelId" label="备用模型ID" />
      <el-table-column prop="enabled" label="启用" width="80" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-permission="'admin:llm:edit'" text @click="openRoute(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="pVisible" title="提供方" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="pForm.name" /></el-form-item>
        <el-form-item label="Base URL"><el-input v-model="pForm.baseUrl" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="pForm.apiKey" type="password" placeholder="留空则不改" show-password /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="pForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mVisible" title="模型" width="480px">
      <el-form label-width="100px">
        <el-form-item label="提供方ID"><el-input-number v-model="mForm.providerId" /></el-form-item>
        <el-form-item label="模型编码"><el-input v-model="mForm.modelCode" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="mForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mVisible = false">取消</el-button>
        <el-button type="primary" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rVisible" title="路由" width="480px">
      <el-form label-width="110px">
        <el-form-item label="用途"><el-input v-model="rForm.purposeCode" disabled /></el-form-item>
        <el-form-item label="主模型ID"><el-input-number v-model="rForm.primaryModelId" /></el-form-item>
        <el-form-item label="备用模型ID"><el-input-number v-model="rForm.backupModelId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const providers = ref<any[]>([])
const models = ref<any[]>([])
const routes = ref<any[]>([])
const pVisible = ref(false)
const mVisible = ref(false)
const rVisible = ref(false)
const pForm = reactive<any>({ id: null, name: '', baseUrl: '', apiKey: '', enabled: 1, protocol: 'openai_compat' })
const mForm = reactive<any>({ id: null, providerId: 1, modelCode: '', enabled: 1 })
const rForm = reactive<any>({ id: null, purposeCode: '', primaryModelId: null, backupModelId: null, enabled: 1 })

async function load() {
  const [p, m, r] = await Promise.all([
    http.get('/admin/llm/providers'),
    http.get('/admin/llm/models'),
    http.get('/admin/llm/routes'),
  ])
  providers.value = p.data.data
  models.value = m.data.data
  routes.value = r.data.data
}

function openProvider(row?: any) {
  if (row) Object.assign(pForm, { ...row, apiKey: '' })
  else Object.assign(pForm, { id: null, name: '', baseUrl: '', apiKey: '', enabled: 1, protocol: 'openai_compat' })
  pVisible.value = true
}

function openModel(row?: any) {
  if (row) Object.assign(mForm, row)
  else Object.assign(mForm, { id: null, providerId: providers.value[0]?.id || 1, modelCode: '', enabled: 1 })
  mVisible.value = true
}

function openRoute(row: any) {
  Object.assign(rForm, row)
  rVisible.value = true
}

async function saveProvider() {
  const payload = { ...pForm, protocol: 'openai_compat' }
  if (pForm.id) await http.put(`/admin/llm/providers/${pForm.id}`, payload)
  else await http.post('/admin/llm/providers', payload)
  ElMessage.success('已保存（Key 不会回显明文）')
  pVisible.value = false
  await load()
}

async function saveModel() {
  if (mForm.id) await http.put(`/admin/llm/models/${mForm.id}`, mForm)
  else await http.post('/admin/llm/models', mForm)
  ElMessage.success('已保存')
  mVisible.value = false
  await load()
}

async function saveRoute() {
  await http.put(`/admin/llm/routes/${rForm.id}`, rForm)
  ElMessage.success('已保存')
  rVisible.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
h2, h3 { margin: 16px 0 8px; }
.mt { margin-top: 8px; }
</style>
