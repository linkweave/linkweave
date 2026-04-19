import { z } from 'zod'
import { colorHexSchema } from './shared'
import { v } from './validation-messages'

export const tagSaveSchema = z.object({
  collectionId: z.string().min(1, v.collectionIdRequired()),
  name: z.string().min(1, v.required('Name')).max(50, v.maxLength('Name', 50)).trim(),
  color: colorHexSchema,
})

export type TagSaveInput = z.infer<typeof tagSaveSchema>
