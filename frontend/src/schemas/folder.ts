import { z } from 'zod'
import type { TFunction } from './types'

export const folderSaveSchema = (t: TFunction) =>
  z.object({
    collectionId: z.string().min(1, t('validation.collectionIdRequired')),
    parentId: z.string().optional(),
    name: z.string().min(1, t('validation.required', { field: 'Name' })).trim(),
  })

export type FolderSaveInput = z.infer<ReturnType<typeof folderSaveSchema>>
