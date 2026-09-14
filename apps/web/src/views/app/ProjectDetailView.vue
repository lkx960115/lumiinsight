<template>
  <div v-if="project">
    <div class="page-head">
      <div>
        <h2>{{ project.name }}</h2>
        <p class="muted">{{ project.brand }} {{ project.mainModel }} · 评论 {{ project.reviewCount }} 条</p>
      </div>
      <el-button @click="router.push('/app/projects')">返回列表</el-button>
    </div>

    <el-card class="block">
      <template #header>导入评论（Excel / CSV）</template>
      <p class="muted">模板列：平台、原文（必填），时间、商品ID、评论ID、点赞、作者匿名ID、商品名、链接。</p>
      <el-upload :show-file-list="false" :http-request="upload" accept=".xlsx,.xls,.csv">
        <el-button v-permission="'import:execute'" type="primary">选择文件导入</el-button>
      </el-upload>
      <el-table :data="jobs.records" class="mt">
        <el-table-column prop="filename" label="文件" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="successRows" label="成功" width="80" />
        <el-table-column prop="failRows" label="失败" width="80" />
        <el-table-column prop="message" label="说明" />
        <el-table-column label="报告" width="120">
          <template #default="{ row }">
            <el-button v-if="row.errorObjectKey" text @click="downloadErrors(row.id)">下载失败行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="block">
      <template #header>评论列表（展示已脱敏）</template>
      <el-form inline>
        <el-form-item>
          <el-select v-model="platform" clearable placeholder="平台" style="width: 140px" @change="loadReviews">
            <el-option label="小红书" value="xiaohongshu" />
            <el-option label="京东" value="jd" />
            <el-option label="淘宝" value="taobao" />
            <el-option label="抖音" value="douyin" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="keyword" placeholder="搜索原文" @keyup.enter="loadReviews" />
        </el-form-item>
        <el-button @click="loadReviews">查询</el-button>
      </el-form>
      <el-table :data="reviews.records">
        <el-table-column prop="platform" label="平台" width="120" />
        <el-table-column prop="content" label="原文" />
        <el-table-column prop="reviewTime" label="时间" width="180" />
        <el-table-column prop="likeCount" label="点赞" width="80" />
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next"
        :total="reviews.total"
        v-model:current-page="reviewPage"
        @current-change="loadReviews"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const project = ref<any>(null)
const jobs = reactive({ records: [] as any[] })
const reviews = reactive({ records: [] as any[], total: 0 })
const platform = ref('')
const keyword = ref('')
const reviewPage = ref(1)

async function load() {
  const { data } = await http.get(`/projects/${id}`)
  project.value = data.data
  await loadJobs()
  await loadReviews()
}

async function loadJobs() {
  const { data } = await http.get(`/projects/${id}/imports`)
  jobs.records = data.data.records
}

async function loadReviews() {
  const { data } = await http.get(`/projects/${id}/reviews`, {
    params: { page: reviewPage.value, size: 10, platform: platform.value || undefined, keyword: keyword.value || undefined },
  })
  reviews.records = data.data.records
  reviews.total = Number(data.data.total)
}

async function upload(opt: UploadRequestOptions) {
  const form = new FormData()
  form.append('file', opt.file)
  await http.post(`/projects/${id}/imports`, form)
  ElMessage.success('已提交导入任务')
  setTimeout(load, 800)
}

async function downloadErrors(jobId: number) {
  const resp = await http.get(`/imports/${jobId}/errors`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'import-errors.csv'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<style scoped>
h2 { margin: 0; }
.block { margin-bottom: 16px; }
.mt { margin-top: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
