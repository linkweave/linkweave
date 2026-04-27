import { extractErrorSummary, isNetworkError } from '@/api/error-utils'
import { defineStore } from 'pinia'
import { toast } from 'vue-sonner'


export const useNotificationStore = defineStore('notification', () => {
  // NOSONAR(typescript:S7721)
  function success(message: string) {
    toast.success(message)
  }

  function error(message: string) {
    toast.error(message)
  }

  function warning(message: string) {
    toast.warning(message)
  }

  function info(message: string) {
    toast.info(message)
  }

  function successWithUndo(message: string, undoLabel: string, onUndo: () => void | Promise<void>) {
    toast.success(message, {
      action: {
        label: undoLabel,
        onClick: async () => {
          try {
            await onUndo()
          } catch {
            toast.error('Undo failed')
          }
        },
      },
    })
  }

  async function handleApiError(err: unknown, fallbackMessage = 'An unexpected error occurred') {
    if (isNetworkError(err)) {
      toast.error(fallbackMessage)
      return
    }
    const message = await extractErrorSummary(err, fallbackMessage)
    toast.error(message)
  }

  return {
    success,
    error,
    warning,
    info,
    successWithUndo,
    handleApiError,
  }
})
