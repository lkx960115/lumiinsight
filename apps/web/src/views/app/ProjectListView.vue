<template>
  <div>
    <div class="page-head">
      <div>
        <h2>项目</h2>
        <p class="muted">按品牌、型号看评论洞察。数据来自商家后台导出后导入，不在这里抓取平台。</p>
      </div>
      <div class="page-actions">
        <el-button text @click="guideVisible = true">怎么走一遍</el-button>
        <el-button v-permission="'project:edit'" type="primary" @click="openEdit()">新建项目</el-button>
      </div>
    </div>
    <div class="panel">
    <el-table :data="table.records" v-loading="loading" empty-text="还没有项目。点右上角新建。">
      <el-table-column label="项目" min-width="180">
        <template #default="{ row }">
          <a class="name-link" @click.prevent="router.push(`/app/projects/${row.id}`)">{{ row.name }}</a>
          <p class="cell-note">评论 {{ row.reviewCount || 0 }} 条</p>
        </template>
      </el-table-column>
      <el-table-column label="品牌" min-width="100">
        <template #default="{ row }">{{ row.brand || '—' }}</template>
      </el-table-column>
      <el-table-column label="主型号" min-width="100">
        <template #default="{ row }">{{ row.mainModel || '—' }}</template>
      </el-table-column>
      <el-table-column label="品类" min-width="100">
        <template #default="{ row }">{{ row.category || '—' }}</template>
      </el-table-column>
      <el-table-column label="平台" min-width="200">
        <template #default="{ row }">{{ platformListLabel(row.platforms) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="168">
        <template #default="{ row }">{{ formatClock(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-permission="'project:edit'" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'project:delete'" text type="danger" @click="onDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="pager"
      background
      hide-on-single-page
      layout="total, prev, pager, next"
      :page-size="10"
      :total="table.total"
      v-model:current-page="page"
      @current-change="load"
    />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑项目' : '新建项目'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="form.brand" /></el-form-item>
        <el-form-item label="主型号"><el-input v-model="form.mainModel" placeholder="例如演示吸顶灯" /></el-form-item>
        <el-form-item label="竞品"><el-input v-model="competitorsText" placeholder="例如星辉竞品灯" /></el-form-item>
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

    <el-dialog v-model="guideVisible" title="怎么走一遍" width="560px">
      <ol class="guide-steps">
        <li>新建项目。用样例时，主型号填「演示吸顶灯」，竞品填「星辉竞品灯」。</li>
        <li>进入项目，点「下载样例」一次导入即可，或从商家后台导出评价表再导入。</li>
        <li>点「清洗并分析」。可以离开页面，稍后再回来看结果。</li>
        <li>看图表、按月走势和原评。评论列表可改成「按意思找」。</li>
        <li>侧栏「报告」打开结论，点「查看原评」核对原文。摘要和建议会带原评，配了摘要模型时更像顾问口吻。报告有「问原评」一节，竞品可按方面、按月对照。项目详情口碑预警会提示负向变多、新槽点和声量骤降；右侧可点「问原评」，只根据本项目原评回答。</li>
        <li>需要带走时，在报告页导出 PDF、表格或文稿。</li>
      </ol>
      <p class="muted">分析服务未启动、或还没配模型 Key 时，会提示失败或按词典降级，不要当成系统坏了。</p>
      <template #footer>
        <el-button @click="guideVisible = false">关闭</el-button>
        <el-button v-permission="'project:edit'" type="primary" @click="guideThenCreate">去新建项目</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { formatClock, platformListLabel } from '@/utils/labels'

const router = useRouter()
const loading = ref(false)
const page = ref(1)
const table = reactive({ records: [] as any[], total: 0 })
const visible = ref(false)
const guideVisible = ref(false)
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

function guideThenCreate() {
  guideVisible.value = false
  openEdit()
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
  else {
    await http.post('/projects', payload)
    page.value = 1
  }
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除项目「${row.name}」后，评论、报告和检索数据会一并清除，且无法恢复。`, '删除项目', { type: 'warning' })
  await http.delete(`/projects/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
