import { z } from 'zod'
import type { TFunction } from './types'

export const httpUrlSchema = (t: TFunction) =>
  z
    .string()
    .min(1, t('validation.required', { field: 'URL' }))
    .trim()
    .url(t('validation.url'))
    .refine(
      (val) => val.startsWith('http://') || val.startsWith('https://'),
      t('validation.urlScheme'),
    )

export const colorHexSchema = (t: TFunction) =>
  z
    .string()
    .trim()
    .refine(
      (val) => val === '' || /^#[0-9a-fA-F]{6}$/.test(val),
      t('validation.colorHex'),
    )
    .optional()
