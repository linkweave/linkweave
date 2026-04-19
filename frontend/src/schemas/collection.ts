import { z } from 'zod'
import { v } from './validation-messages'

export const collectionCreateSchema = z.object({
  name: z.string().min(1, v.required('Name')).max(255, v.maxLength('Name', 255)).trim(),
})

export type CollectionCreateInput = z.infer<typeof collectionCreateSchema>

export const collectionDeleteSchema = z
  .object({
    confirmName: z.string().min(1, v.required('Collection name')),
    expectedName: z.string(),
  })
  .refine((data) => data.confirmName === data.expectedName, {
    message: v.nameMismatch(),
    path: ['confirmName'],
  })

export type CollectionDeleteInput = z.infer<typeof collectionDeleteSchema>

export const collectionShareSchema = z.object({
  email: z.string().min(1, v.required('Email')).email(v.email()),
})

export type CollectionShareInput = z.infer<typeof collectionShareSchema>
