<template>
  <el-dialog
    :model-value="modelValue"
    :title="forced ? '请修改密码' : '修改密码'"
    width="480px"
    :close-on-click-modal="!forced"
    :close-on-press-escape="!forced"
    :show-close="!forced"
    @close="onClose"
  >
    <p v-if="forced" class="muted">首次登录或管理员重置后，需要先改成自己的密码，才能继续使用。</p>
    <el-form label-width="90px">
      <el-form-item label="当前密码">
        <el-input v-model="currentPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 8 位" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onCancel">{{ forced ? '退出' : '取消' }}</el-button>
      <el-button type="primary" :disabled="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ modelValue: boolean; forced?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      currentPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
    }
  },
)

function onClose() {
  if (props.forced && auth.user?.mustChangePwd) {
    emit('update:modelValue', true)
    return
  }
  emit('update:modelValue', false)
}

function onCancel() {
  if (props.forced) {
    auth.logout()
    router.push('/login')
    return
  }
  emit('update:modelValue', false)
}

async function save() {
  if (!currentPassword.value) {
    ElMessage.error('请输入当前密码')
    return
  }
  if (newPassword.value.trim().length < 8) {
    ElMessage.error('新密码至少 8 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await http.post('/auth/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value.trim(),
    })
    await auth.fetchMe()
    ElMessage.success('已保存')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>
