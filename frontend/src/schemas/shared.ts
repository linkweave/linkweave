import { z } from 'zod'
import { parseAbsoluteUrl } from '@/lib/url'
import type { TFunction } from './types'

// Not `.url()`: zod 3's url check is `new URL()`, whose host parsing is
// runtime-dependent — Chromium percent-encodes whitespace into the host
// (`https://two words` passes) while the backend's `URI.create` rejects it.
// `parseAbsoluteUrl` adds an explicit authority-whitespace guard so the
// verdict is identical in every runtime and matches the backend.
export const httpUrlSchema = (t: TFunction) =>
  z
    .string()
    .min(1, t('validation.required', { field: 'URL' }))
    .trim()
    .refine((val) => parseAbsoluteUrl(val) !== null, t('validation.url'))
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
