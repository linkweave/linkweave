import { z } from 'zod'
import type { TFunction } from './types'

export const collectionCreateSchema = (t: TFunction) =>
  z.object({
    name: z
      .string()
      .min(1, t('validation.required', { field: 'Name' }))
      .max(255, t('validation.maxLength', { field: 'Name', max: 255 }))
      .trim(),
  })

export type CollectionCreateInput = z.infer<ReturnType<typeof collectionCreateSchema>>

export const collectionDeleteSchema = (t: TFunction) =>
  z
    .object({
      confirmName: z.string().min(1, t('validation.required', { field: 'Collection name' })),
      expectedName: z.string(),
    })
    .refine((data) => data.confirmName === data.expectedName, {
      message: t('validation.nameMismatch'),
      path: ['confirmName'],
    })

export type CollectionDeleteInput = z.infer<ReturnType<typeof collectionDeleteSchema>>

export const collectionShareSchema = (t: TFunction) =>
  z.object({
    email: z
      .string()
      .min(1, t('validation.required', { field: 'Email' }))
      .email(t('validation.email')),
  })

export type CollectionShareInput = z.infer<ReturnType<typeof collectionShareSchema>>
