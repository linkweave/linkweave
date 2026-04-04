import { FailureType } from '@/api/generated'
import { ResponseError } from '@/api/generated/runtime'
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

  async function handleApiError(err: unknown, fallbackMessage = 'An unexpected error occurred') {
    if (err instanceof ResponseError) {
      try {
        const body = await err.response.json()
        if (body.type === FailureType.Validation && Array.isArray(body.violations)) {
          const messages = body.violations.map((v: { message: string }) => v.message).join(', ')
          toast.error(messages)
        } else if (body.type === FailureType.Failure && body.summary) {
          toast.error(body.summary)
        } else {
          toast.error(fallbackMessage)
        }
      } catch {
        toast.error(fallbackMessage)
      }
    } else {
      toast.error(fallbackMessage)
    }
  }

  return {
    success,
    error,
    warning,
    info,
    handleApiError,
  }
})
