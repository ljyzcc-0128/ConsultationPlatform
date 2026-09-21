import { reactive } from 'vue'

/** 全局 Toast（模块级单例状态） */
const toasts = reactive([])
let seq = 0

export function useToast() {
  function push(message, type = 'info', duration = 2600) {
    const id = ++seq
    toasts.push({ id, message, type })
    setTimeout(() => {
      const i = toasts.findIndex(t => t.id === id)
      if (i > -1) toasts.splice(i, 1)
    }, duration)
  }
  return {
    toasts,
    success: (m) => push(m, 'success'),
    error: (m) => push(m, 'error', 4200),
    info: (m) => push(m)
  }
}
