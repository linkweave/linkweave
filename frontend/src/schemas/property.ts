import { z } from 'zod'
import { PropertyType } from '@/api/generated'
import type { TFunction } from './types'

/**
 * Mirrors `PropertyDefinitionSaveJson` from the generated client, with two
 * additions on top of structural typing:
 *
 *  - `allowedValues` is coerced from "empty after trim" to `undefined`, so the
 *    payload sent over the wire doesn't carry a useless empty string.
 *  - When the property type is `SELECT` or `MULTI_SELECT`, `allowedValues`
 *    becomes required (you can't have a select with no options to pick from).
 *    Other types ignore the field entirely.
 */
export const propertyDefinitionSaveSchema = (t: TFunction) =>
  z
    .object({
      collectionId: z.string().min(1, t('validation.collectionIdRequired')),
      // Names act as identifiers in the search syntax (`property:<name>=…`),
      // so we restrict them to characters the tokenizer can parse unquoted:
      // letters, digits, underscore, hyphen. The regex matches what
      // `parsePropertyValue` accepts on the read side — keep these in sync.
      name: z
        .string()
        .trim()
        .min(1, t('validation.required', { field: t('property.fieldName') }))
        .max(50, t('validation.maxLength', { field: t('property.fieldName'), max: 50 }))
        .regex(/^[\w-]+$/, t('property.errorNameFormat')),
      type: z.nativeEnum(PropertyType),
      sortOrder: z.number().int(),
      allowedValues: z
        .string()
        .trim()
        .optional()
        .transform((v) => (v && v.length > 0 ? v : undefined)),
    })
    .superRefine((data, ctx) => {
      const needsOptions =
        data.type === PropertyType.Select || data.type === PropertyType.MultiSelect
      if (needsOptions && !data.allowedValues) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['allowedValues'],
          message: t('validation.required', { field: t('property.fieldOptions') }),
        })
      }
    })

export type PropertyDefinitionSaveInput = z.infer<ReturnType<typeof propertyDefinitionSaveSchema>>
