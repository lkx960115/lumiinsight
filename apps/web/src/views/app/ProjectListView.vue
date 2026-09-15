<template>
  <div>
    <div class="page-head">
      <div>
        <h2>项目</h2>
        <p class="muted">分析单元：品牌、型号、平台范围。采集勾选不触发拉取，数据只走导入。</p>
      </div>
      <div class="page-actions">
        <el-button v-permission="'project:edit'" type="primary" @click="openEdit()">新建项目</el-button>
      </div>
    </div>
    <div class="panel">
    <el-table :data="table.records" v-loading="loading">
      <el-table-column label="名称">
        <template #default="{ row }">
          <a class="name-link" @click.prevent="router.push(`/app/projects/${row.id}`)">{{ row.name }}</a>
        </template>
      </el-table-column>
      <el-table-column prop="brand" label="品牌" width="120" />
      <el-table-column prop="mainModel" label="主型号" width="160" />
      <el-table-column prop="reviewCount" label="评论数" width="100" />
      <el-table-column label="操作" width="140" align="left">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-permission="'project:edit'" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'project:delete'" text type="danger" @click="onDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>
    <el-pagination
      class="pager"
      background
      layout="prev, pager, next"
      :total="table.total"
      v-model:current-page="page"
      @current-change="load"
    />

    <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="主型号"><el-input v-model="form.mainModel" /></el-form-item>
        <el-form-item label="竞品"><el-input v-model="competitorsText" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="keywordsText" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="平台">
          <el-checkbox-group v-model="form.platforms">
            <el-checkbox label="xiaohongshu">小红书</el-checkbox>
            <el-checkbox label="jd">京东</el-checkbox>
            <el-checkbox label="taobao">淘宝</el-checkbox>
            <el-checkbox label="douyin">抖音</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const router = useRouter()
const loading = ref(false)
const page = ref(1)
const table = reactive({ records: [] as any[], total: 0 })
const visible = ref(false)
const competitorsText = ref('')
const keywordsText = ref('')
const form = reactive<any>({
  id: null,
  name: '',
  category: '',
  brand: '',
  mainModel: '',
  platforms: [] as string[],
})

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/projects', { params: { page: page.value, size: 10 } })
    table.records = data.data.records
    table.total = Number(data.data.total)
  } finally {
    loading.value = false
  }
}

function openEdit(row?: any) {
  if (row) {
    Object.assign(form, row)
    competitorsText.value = (row.competitors || []).join(',')
    keywordsText.value = (row.keywords || []).join(',')
  } else {
    Object.assign(form, { id: null, name: '', category: '', brand: '', mainModel: '', platforms: [] })
    competitorsText.value = ''
    keywordsText.value = ''
  }
  visible.value = true
}

function split(v: string) {
  return v.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
}

async function save() {
  const payload = {
    name: form.name,
    category: form.category,
    brand: form.brand,
    mainModel: form.mainModel,
    competitors: split(competitorsText.value),
    keywords: split(keywordsText.value),
    platforms: form.platforms,
  }
  if (form.id) await http.put(`/projects/${form.id}`, payload)
  else await http.post('/projects', payload)
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除项目「${row.name}」？评论会随逻辑删除策略处理。`, '高风险操作', { type: 'warning' })
  await http.delete(`/projects/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
h2 { margin: 0; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
