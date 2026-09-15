import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    if (binding.value && !auth.has(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  },
}
