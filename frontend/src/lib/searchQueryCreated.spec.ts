import { describe, expect, it } from 'vitest'
import { matchesCreated, parseCreatedValue } from './searchQueryCreated'

// Anchor "today" so the tests are not time-sensitive. Local-timezone Dates;
// the parser/matcher both operate in local time deliberately (users think of
// "today" in their own timezone).
const NOW = new Date(2026, 4, 16, 10, 30, 0) // 2026-05-16 10:30 local

describe('parseCreatedValue — absolute date literals', () => {
  it('parses ISO YYYY-MM-DD', () => {
    const r = parseCreatedValue('2026-05-01', NOW)
    expect(r?.op).toBe('eq')
    expect([r?.day.getFullYear(), r?.day.getMonth(), r?.day.getDate()]).toEqual([2026, 4, 1])
  })

  it('parses dd.MM.yyyy with and without zero padding', () => {
    const padded = parseCreatedValue('01.05.2026', NOW)
    expect([padded?.day.getFullYear(), padded?.day.getMonth(), padded?.day.getDate()]).toEqual([2026, 4, 1])
    const bare = parseCreatedValue('1.5.2026', NOW)
    expect([bare?.day.getFullYear(), bare?.day.getMonth(), bare?.day.getDate()]).toEqual([2026, 4, 1])
  })

  it('rejects calendar overflow in either format', () => {
    expect(parseCreatedValue('2026-02-30', NOW)).toBeNull()
    expect(parseCreatedValue('30.02.2026', NOW)).toBeNull()
    expect(parseCreatedValue('2026-13-01', NOW)).toBeNull()
    expect(parseCreatedValue('01.13.2026', NOW)).toBeNull()
  })

  it('returns null for unrecognized values', () => {
    expect(parseCreatedValue('not-a-date', NOW)).toBeNull()
    expect(parseCreatedValue('', NOW)).toBeNull()
    expect(parseCreatedValue('2026', NOW)).toBeNull() // year-only not supported
  })
})

describe('parseCreatedValue — comparators', () => {
  it('parses leading > and < on every value form', () => {
    expect(parseCreatedValue('>2026-05-01', NOW)?.op).toBe('gt')
    expect(parseCreatedValue('<2026-05-01', NOW)?.op).toBe('lt')
    expect(parseCreatedValue('>01.05.2026', NOW)?.op).toBe('gt')
    expect(parseCreatedValue('<today', NOW)?.op).toBe('lt')
    expect(parseCreatedValue('>today-2w', NOW)?.op).toBe('gt')
    expect(parseCreatedValue('<today-1y', NOW)?.op).toBe('lt')
  })

  it('without a comparator defaults to equality', () => {
    expect(parseCreatedValue('2026-05-01', NOW)?.op).toBe('eq')
    expect(parseCreatedValue('today', NOW)?.op).toBe('eq')
  })
})

describe('parseCreatedValue — relative offsets', () => {
  it('today resolves to "now" at local midnight', () => {
    const r = parseCreatedValue('today', NOW)
    expect([r?.day.getFullYear(), r?.day.getMonth(), r?.day.getDate()]).toEqual([2026, 4, 16])
    expect(r?.day.getHours()).toBe(0)
    expect(r?.day.getMinutes()).toBe(0)
  })

  it('today-N defaults to days', () => {
    const bare = parseCreatedValue('today-30', NOW)
    const explicit = parseCreatedValue('today-30d', NOW)
    expect(bare?.day.getTime()).toBe(explicit?.day.getTime())
  })

  it('weeks (w) walks by 7 days each; crosses month boundary cleanly', () => {
    // 2026-05-16 minus 3 weeks = 2026-04-25
    const r = parseCreatedValue('today-3w', NOW)
    expect([r?.day.getFullYear(), r?.day.getMonth(), r?.day.getDate()]).toEqual([2026, 3, 25])
  })

  it('years (y) walks by calendar year', () => {
    const r = parseCreatedValue('today-1y', NOW)
    expect([r?.day.getFullYear(), r?.day.getMonth(), r?.day.getDate()]).toEqual([2025, 4, 16])
  })

  it('year subtraction from a leap-day rolls forward when target year is not a leap year', () => {
    // 2024-02-29 minus 1 year — 2023 has no Feb 29, so setFullYear lands on
    // March 1. Documenting the chosen semantics (JS Date default) rather than
    // implementing custom clamping.
    const leapNow = new Date(2024, 1, 29) // Feb 29, 2024
    const r = parseCreatedValue('today-1y', leapNow)
    expect([r?.day.getFullYear(), r?.day.getMonth(), r?.day.getDate()]).toEqual([2023, 2, 1])
  })

  it('zero offsets are equivalent to today across all units', () => {
    const t = parseCreatedValue('today', NOW)?.day.getTime()
    expect(parseCreatedValue('today-0', NOW)?.day.getTime()).toBe(t)
    expect(parseCreatedValue('today-0d', NOW)?.day.getTime()).toBe(t)
    expect(parseCreatedValue('today-0w', NOW)?.day.getTime()).toBe(t)
    expect(parseCreatedValue('today-0y', NOW)?.day.getTime()).toBe(t)
  })

  it('rejects forward offsets and unsupported units', () => {
    expect(parseCreatedValue('today+5d', NOW)).toBeNull()
    expect(parseCreatedValue('today-1m', NOW)).toBeNull() // months not supported
    expect(parseCreatedValue('today-1mo', NOW)).toBeNull()
  })
})

describe('matchesCreated', () => {
  const day = parseCreatedValue('2026-05-16', NOW)!

  it('eq matches any time on the day, rejects either side', () => {
    expect(matchesCreated(new Date(2026, 4, 16, 0, 0, 0), day)).toBe(true)
    expect(matchesCreated(new Date(2026, 4, 16, 23, 59, 59), day)).toBe(true)
    expect(matchesCreated(new Date(2026, 4, 15, 23, 59, 59), day)).toBe(false)
    expect(matchesCreated(new Date(2026, 4, 17, 0, 0, 0), day)).toBe(false)
  })

  it('gt excludes the day itself', () => {
    const gt = parseCreatedValue('>2026-05-16', NOW)!
    expect(matchesCreated(new Date(2026, 4, 16, 23, 59, 59), gt)).toBe(false)
    expect(matchesCreated(new Date(2026, 4, 17, 0, 0, 0), gt)).toBe(true)
  })

  it('lt excludes the day itself', () => {
    const lt = parseCreatedValue('<2026-05-16', NOW)!
    expect(matchesCreated(new Date(2026, 4, 16, 0, 0, 0), lt)).toBe(false)
    expect(matchesCreated(new Date(2026, 4, 15, 23, 59, 59), lt)).toBe(true)
  })

  it('relative >today-1w matches things created within the last week (today inclusive)', () => {
    const gt = parseCreatedValue('>today-1w', NOW)!
    // NOW = 2026-05-16, threshold = 2026-05-09; gt excludes the 9th itself.
    expect(matchesCreated(new Date(2026, 4, 10, 0, 0, 0), gt)).toBe(true)
    expect(matchesCreated(new Date(2026, 4, 16, 8, 0, 0), gt)).toBe(true)
    expect(matchesCreated(new Date(2026, 4, 9, 23, 0, 0), gt)).toBe(false)
  })

  it('relative <today-1y matches things older than a year ago', () => {
    const lt = parseCreatedValue('<today-1y', NOW)!
    // threshold = 2025-05-16; lt excludes the 16th itself.
    expect(matchesCreated(new Date(2024, 0, 1), lt)).toBe(true)
    expect(matchesCreated(new Date(2025, 4, 15), lt)).toBe(true)
    expect(matchesCreated(new Date(2025, 4, 16, 0, 0, 0), lt)).toBe(false)
  })
})
