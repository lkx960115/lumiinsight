<template>
  <div>
    <h2>操作日志</h2>
    <el-table :data="table.records">
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="action" label="动作" width="180" />
      <el-table-column prop="resource" label="对象" width="120" />
      <el-table-column prop="detail" label="详情" />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import http from '@/api/http'

const table = reactive({ records: [] as any[] })
onMounted(async () => {
  const { data } = await http.get('/admin/audit')
  table.records = data.data.records
})
</script>
