<template>
  <div>
    <h2>导入任务</h2>
    <el-table :data="table.records">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="projectId" label="项目" width="80" />
      <el-table-column prop="filename" label="文件" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="successRows" label="成功" width="80" />
      <el-table-column prop="failRows" label="失败" width="80" />
      <el-table-column prop="message" label="说明" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import http from '@/api/http'

const table = reactive({ records: [] as any[] })

onMounted(async () => {
  const { data } = await http.get('/admin/jobs')
  table.records = data.data.records
})
</script>
