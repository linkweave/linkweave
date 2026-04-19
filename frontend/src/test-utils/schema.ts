import { createI18n } from 'vue-i18n'
import en from '@/i18n/locales/en.json'
import type { TFunction } from '@/schemas/types'

// Standalone translator for schema tests.
// Schemas now require a TFunction (normally useI18n().t from a Vue component),
// but unit tests run in Node without a component context. This provides the same
// English translations without touching localStorage or the main i18n instance.
const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })

export const standaloneSchemaTranslator: TFunction = (key, params) =>
  i18n.global.t(key, params as Record<string, unknown>)
