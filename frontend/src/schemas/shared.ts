import { z } from 'zod'
import { v } from './validation-messages'

export const httpUrlSchema = z
  .string()
  .min(1, v.required('URL'))
  .trim()
  .url(v.url())
  .refine(
    (val) => val.startsWith('http://') || val.startsWith('https://'),
    v.urlScheme(),
  )

export const colorHexSchema = z
  .string()
  .trim()
  .refine(
    (val) => val === '' || /^#[0-9a-fA-F]{6}$/.test(val),
    v.colorHex(),
  )
  .optional()
