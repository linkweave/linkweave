import { z } from 'zod'
import { v } from './validation-messages'

export const folderSaveSchema = z.object({
  collectionId: z.string().min(1, v.collectionIdRequired()),
  parentId: z.string().optional(),
  name: z.string().min(1, v.required('Name')).trim(),
})

export type FolderSaveInput = z.infer<typeof folderSaveSchema>
