<template>
  <div>
    <h2>方面词典</h2>
    <p class="muted">ABSA 方面名必须落在词典或「其它」。本周只读维护入口，改词后需重跑分析才会作用到历史。</p>
    <el-table :data="rows">
      <el-table-column prop="sortNo" label="顺序" width="80" />
      <el-table-column prop="name" label="方面" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const rows = ref<any[]>([])
onMounted(async () => {
  const { data } = await http.get('/admin/dicts/aspects')
  rows.value = data.data
})
</script>
