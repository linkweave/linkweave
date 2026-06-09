import { z } from 'zod'
import type { TFunction } from './types'

export function apiKeyCreateSchema(t: TFunction) {
  return z.object({
    name: z
      .string()
      .min(1, t('apiKeys.validation.nameRequired'))
      .max(100, t('apiKeys.validation.nameTooLong'))
      .refine((v) => v.trim().length > 0, t('apiKeys.validation.nameBlank')),
    // '' means "never"; the other values mirror the select options in ApiKeyCreateDialog.
    expiresIn: z.enum(['', '30d', '90d', '1y']),
  })
}
