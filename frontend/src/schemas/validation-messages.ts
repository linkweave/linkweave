import { createI18n } from 'vue-i18n'
import en from '@/i18n/locales/en.json'

// Lightweight standalone i18n instance for validation messages.
// Avoids importing the full i18n module which accesses localStorage at import time,
// breaking vitest's node environment. Falls back to English always, which is correct
// for server-side validation messages.
const validationI18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: { en },
})

function t(key: string, params?: Record<string, string | number>): string {
  return validationI18n.global.t(key, params as Record<string, unknown>)
}

export const v = {
  required: (field: string) => t('validation.required', { field }),
  email: () => t('validation.email'),
  url: () => t('validation.url'),
  urlScheme: () => t('validation.urlScheme'),
  minLength: (field: string, min: number) => t('validation.minLength', { field, min }),
  maxLength: (field: string, max: number) => t('validation.maxLength', { field, max }),
  passwordMismatch: () => t('validation.passwordMismatch'),
  nameMismatch: () => t('validation.nameMismatch'),
  colorHex: () => t('validation.colorHex'),
  collectionIdRequired: () => t('validation.collectionIdRequired'),
}
