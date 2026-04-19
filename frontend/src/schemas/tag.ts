import { z } from 'zod'
import { colorHexSchema } from './shared'
import type { TFunction } from './types'

export const tagSaveSchema = (t: TFunction) =>
  z.object({
    collectionId: z.string().min(1, t('validation.collectionIdRequired')),
    name: z
      .string()
      .min(1, t('validation.required', { field: 'Name' }))
      .max(50, t('validation.maxLength', { field: 'Name', max: 50 }))
      .trim(),
    color: colorHexSchema(t),
  })

export type TagSaveInput = z.infer<ReturnType<typeof tagSaveSchema>>
