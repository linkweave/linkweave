import { z } from 'zod'
import type { TFunction } from './types'

/**
 * Validates that a string is a valid JavaScript regex.
 * Invalid patterns (e.g. unclosed groups) produce a clear error.
 */
export const regexPatternSchema = (t: TFunction) =>
  z
    .string()
    .min(1, t('validation.required', { field: 'Pattern' }))
    .trim()
    .max(2000, t('validation.maxLength', { field: 'Pattern', max: 2000 }))
    .refine((val) => {
      try {
        new RegExp(val)
        return true
      } catch {
        return false
      }
    }, t('validation.invalidRegex'))

/**
 * Validates a comma-separated tag-names string:
 * - At least one non-empty tag after splitting on commas
 * - Max 8 tags
 * - Each tag max 50 chars
 */
export const tagNamesSchema = (t: TFunction) =>
  z
    .string()
    .min(1, t('validation.required', { field: 'Tags' }))
    .trim()
    .max(2000, t('validation.maxLength', { field: 'Tags', max: 2000 }))
    .refine((val) => {
      const tags = val
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
      return tags.length > 0
    }, t('validation.atLeastOneTag'))
    .refine((val) => {
      const tags = val
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
      return tags.length <= 8
    }, t('validation.maxTags', { max: 8 }))

export const autoTagRuleSaveSchema = (t: TFunction) =>
  z.object({
    collectionId: z.string().min(1, t('validation.collectionIdRequired')),
    pattern: regexPatternSchema(t),
    tagNames: tagNamesSchema(t),
    description: z
      .string()
      .trim()
      .max(255, t('validation.maxLength', { field: 'Description', max: 255 }))
      .optional()
      .transform((val) => val || undefined),
    enabled: z.boolean(),
  })

export type AutoTagRuleSaveInput = z.infer<ReturnType<typeof autoTagRuleSaveSchema>>
