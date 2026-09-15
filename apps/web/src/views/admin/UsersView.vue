<template>
  <div>
    <div class="page-head">
      <h2>用户与角色</h2>
      <el-button v-permission="'admin:user:edit'" type="primary" @click="open()">新建用户</el-button>
    </div>
    <div class="panel">
      <el-table :data="table.records" empty-text="还没有用户。">
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <div>{{ row.username }}</div>
          <p v-if="row.displayName && row.displayName !== row.username" class="cell-note">{{ row.displayName }}</p>
        </template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色" min-width="120" />
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
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-permission="'admin:user:edit'" text @click="open(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="Boolean(form.id)" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" :placeholder="form.id ? '留空则不改' : ''" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width: 100%">
            <el-option v-for="r in roles" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
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
const canEdit = computed(() => auth.has('admin:user:edit'))
const table = reactive({ records: [] as any[] })
const roles = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ id: null, username: '', displayName: '', password: '', roleCode: 'analyst', enabled: 1 })

async function load() {
  const [users, roleRes] = await Promise.all([
    http.get('/admin/users', { params: { page: 1, size: 50 } }),
    http.get('/admin/users/roles'),
  ])
  table.records = users.data.data.records
  roles.value = roleRes.data.data
}

function open(row?: any) {
  if (row) Object.assign(form, { ...row, password: '' })
  else Object.assign(form, { id: null, username: '', displayName: '', password: '', roleCode: 'analyst', enabled: 1 })
  visible.value = true
}

async function toggle(row: any, enabled: number) {
  try {
    await http.put(`/admin/users/${row.id}`, {
      username: row.username,
      displayName: row.displayName,
      roleCode: row.roleCode,
      enabled,
    })
    ElMessage.success(enabled === 1 ? '已启用' : '已停用')
  } catch {
    row.enabled = enabled === 1 ? 0 : 1
  }
}

async function save() {
  const payload = {
    username: form.username,
    displayName: form.displayName,
    password: form.password,
    roleCode: form.roleCode,
    enabled: form.enabled,
  }
  if (form.id) await http.put(`/admin/users/${form.id}`, payload)
  else await http.post('/admin/users', payload)
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

onMounted(load)
</script>
