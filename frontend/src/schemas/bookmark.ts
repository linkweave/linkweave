import { z } from 'zod'
import { httpUrlSchema } from './shared'
import { v } from './validation-messages'

export const bookmarkSaveSchema = z.object({
  collectionId: z.string().min(1, v.collectionIdRequired()),
  folderId: z.string().optional(),
  title: z.string().min(1, v.required('Title')).trim(),
  url: httpUrlSchema,
  description: z.string().trim().optional().transform(val => val || undefined),
  tagIds: z.set(z.string()).optional(),
})

export type BookmarkSaveInput = z.infer<typeof bookmarkSaveSchema>

export const bookmarkMoveSchema = z.object({
  collectionId: z.string().min(1, v.collectionIdRequired()),
  folderId: z.string().optional(),
})

export type BookmarkMoveInput = z.infer<typeof bookmarkMoveSchema>
