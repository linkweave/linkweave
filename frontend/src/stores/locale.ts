import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, DEFAULT_LOCALE, type SupportedLocale } from '@/i18n'

export const useLocaleStore = defineStore('locale', () => {
  const { locale } = useI18n()

  const currentLocale = ref<SupportedLocale>(locale.value as SupportedLocale)

  watch(currentLocale, (newLocale) => {
    locale.value = newLocale
    localStorage.setItem('locale', newLocale)
  })

  function setLocale(newLocale: SupportedLocale) {
    if (SUPPORTED_LOCALES.includes(newLocale)) {
      currentLocale.value = newLocale
    }
  }

  return {
    currentLocale,
    setLocale,
    supportedLocales: SUPPORTED_LOCALES,
    defaultLocale: DEFAULT_LOCALE
  }
})
