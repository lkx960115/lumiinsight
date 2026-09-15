<template>
  <div>
    <div class="page-head">
      <div>
        <h2>方面词典</h2>
        <p class="muted">方面名必须落在词典或「其它」。改词后请回到项目里重新清洗，再点「触发分析」。</p>
      </div>
      <div class="page-actions">
        <el-button v-permission="'admin:dict:edit'" type="primary" @click="open()">新增方面</el-button>
      </div>
    </div>
    <div class="panel">
      <el-table :data="rows">
        <el-table-column prop="sortNo" label="顺序" width="80" />
        <el-table-column prop="name" label="方面" width="140" />
        <el-table-column prop="keywords" label="匹配词" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-permission="'admin:dict:edit'" text @click="open(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑方面' : '新增方面'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="匹配词"><el-input v-model="form.keywords" placeholder="逗号分隔，如：色温,暖光,冷光" /></el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
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
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const rows = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ id: null, name: '', keywords: '', sortNo: 0, enabled: 1 })

async function load() {
  const { data } = await http.get('/admin/dicts/aspects')
  rows.value = data.data
}

function open(row?: any) {
  if (row) Object.assign(form, { ...row })
  else Object.assign(form, { id: null, name: '', keywords: '', sortNo: 0, enabled: 1 })
  visible.value = true
}

async function save() {
  const payload = {
    name: form.name,
    keywords: form.keywords,
    sortNo: form.sortNo,
    enabled: form.enabled,
  }
  if (form.id) await http.put(`/admin/dicts/aspects/${form.id}`, payload)
  else await http.post('/admin/dicts/aspects', payload)
  ElMessage.success('已保存，请到项目里重新清洗并触发分析')
  visible.value = false
  await load()
}

onMounted(load)
</script>
