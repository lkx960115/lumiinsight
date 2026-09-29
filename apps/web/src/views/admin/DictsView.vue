<template>
  <div>
    <div class="page-head">
      <div>
        <h2>方面词典</h2>
        <p class="muted">方面名必须落在词典或「其它」。改词后请到项目里重新清洗并分析。</p>
      </div>
      <div class="page-actions">
        <el-button v-permission="'admin:dict:edit'" type="primary" @click="open()">新增方面</el-button>
      </div>
    </div>
    <div class="panel">
      <el-table :data="rows" empty-text="还没有方面，请先新增。">
        <el-table-column label="方面" min-width="140">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column prop="keywords" label="匹配词" min-width="200" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              :disabled="!canEdit"
              @change="(val) => toggle(row, val)"
            />
          </template>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canEdit = computed(() => auth.has('admin:dict:edit'))
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

async function toggle(row: any, enabled: number) {
  try {
    await http.put(`/admin/dicts/aspects/${row.id}`, {
      name: row.name,
      keywords: row.keywords,
      sortNo: row.sortNo,
      enabled,
    })
    ElMessage.success(enabled === 1 ? '已启用' : '已停用')
  } catch {
    row.enabled = enabled === 1 ? 0 : 1
  }
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
