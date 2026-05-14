import { useI18n } from 'vue-i18n'

type TimeUnit = 'justNow' | 'minutes' | 'hours' | 'days' | 'weeks' | 'months' | 'years'

const UNITS: { unit: TimeUnit; seconds: number }[] = [
  { unit: 'years', seconds: 365 * 24 * 60 * 60 },
  { unit: 'months', seconds: 30 * 24 * 60 * 60 },
  { unit: 'weeks', seconds: 7 * 24 * 60 * 60 },
  { unit: 'days', seconds: 24 * 60 * 60 },
  { unit: 'hours', seconds: 60 * 60 },
  { unit: 'minutes', seconds: 60 },
]

export function useRelativeTime() {
  const { t } = useI18n()

  function formatRelativeTime(date: Date | string | null | undefined): string {
    if (!date) return ''
    const d = typeof date === 'string' ? new Date(date) : date
    const now = Date.now()
    const diff = Math.floor((now - d.getTime()) / 1000)
    if (diff < 60) return t('relativeTime.justNow')

    for (const { unit, seconds } of UNITS) {
      const count = Math.floor(diff / seconds)
      if (count >= 1) {
        return t(`relativeTime.${unit}`, { count })
      }
    }
    return t('relativeTime.justNow')
  }

  return { formatRelativeTime }
}
