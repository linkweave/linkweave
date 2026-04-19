import { z } from 'zod'
import { httpUrlSchema } from './shared'
import type { TFunction } from './types'

export const bookmarkSaveSchema = (t: TFunction) =>
  z.object({
    collectionId: z.string().min(1, t('validation.collectionIdRequired')),
    folderId: z.string().optional(),
    title: z.string().min(1, t('validation.required', { field: 'Title' })).trim(),
    url: httpUrlSchema(t),
    description: z
      .string()
      .trim()
      .optional()
      .transform((val) => val || undefined),
    tagIds: z.set(z.string()).optional(),
  })

export type BookmarkSaveInput = z.infer<ReturnType<typeof bookmarkSaveSchema>>

export const bookmarkMoveSchema = (t: TFunction) =>
  z.object({
    collectionId: z.string().min(1, t('validation.collectionIdRequired')),
    folderId: z.string().optional(),
  })

export type BookmarkMoveInput = z.infer<ReturnType<typeof bookmarkMoveSchema>>
