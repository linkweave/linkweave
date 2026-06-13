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

const FAVICON_PATTERN = /^(\*\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$/
const BARE_IPV4 = /^\d+\.\d+\.\d+\.\d+$/

export const collectionUpdateSchema = (t: TFunction) =>
  z.object({
    name: z
      .string()
      .min(1, t('validation.required', { field: 'Name' }))
      .max(255, t('validation.maxLength', { field: 'Name', max: 255 }))
      .trim(),
    browserFetchAllowlist: z
      .string()
      .max(2000, t('validation.maxLength', { field: 'Allowlist', max: 2000 }))
      .refine(
        (raw) => {
          if (!raw.trim()) return true
          return raw
            .split('\n')
            .map((p) => p.trim().toLowerCase())
            .filter((p) => p.length > 0)
            .every((p) => FAVICON_PATTERN.test(p) && p !== '*' && !BARE_IPV4.test(p))
        },
        { message: t('collectionManage.faviconAllowlistInvalid') },
      ),
    screenshotEnabled: z.boolean(),
  })

export type CollectionUpdateInput = z.infer<ReturnType<typeof collectionUpdateSchema>>

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
