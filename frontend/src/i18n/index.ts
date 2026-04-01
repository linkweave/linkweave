import { createI18n } from 'vue-i18n'
import en from './locales/en.json'
import de from './locales/de.json'

export const SUPPORTED_LOCALES = ['en', 'de'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: SupportedLocale = 'en'

function detectBrowserLocale(): SupportedLocale | null {
  const browserLang = navigator.language.split('-')[0]
  if (SUPPORTED_LOCALES.includes(browserLang as SupportedLocale)) {
    return browserLang as SupportedLocale
  }
  return null
}

function getStoredLocale(): SupportedLocale | null {
  const stored = localStorage.getItem('locale')
  if (stored && SUPPORTED_LOCALES.includes(stored as SupportedLocale)) {
    return stored as SupportedLocale
  }
  return null
}

export function getInitialLocale(): SupportedLocale {
  return getStoredLocale() ?? detectBrowserLocale() ?? DEFAULT_LOCALE
}

const i18n = createI18n({
  legacy: false,
  locale: getInitialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: {
    en,
    de
  }
})

export default i18n
