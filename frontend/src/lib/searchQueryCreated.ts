// `created:` date matching for the UC-070 search grammar (BR-084 / BR-085).
//
// Accepted value forms (the leading comparator is optional; absent means
// equality):
//   YYYY-MM-DD            ISO
//   dd.MM.yyyy            German day.month.year, zero-padding optional
//   >value | <value       strictly after / before the day
//   today                 today
//   today-N[dwy]?         N days/weeks/years ago; `d` is the default unit
//
// `today` resolves at match time so a long-lived tab eventually rolls over.
// Unparseable values fall back to `null` from `parseCreatedValue`; the matcher
// then treats the token as no-op match-all so a typo doesn't silently hide
// every bookmark.

export type DateOp = 'eq' | 'gt' | 'lt'

export interface ParsedCreated {
  op: DateOp
  day: Date // local midnight of the threshold day
}

const ISO_DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/
// German-style day.month.year — common locally, accepts both zero-padded
// and bare forms (`1.5.2026` and `01.05.2026`).
const DMY_DATE_RE = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/
// `today` alone, or `today-N[dwy]?` for relative offsets. The unit is optional
// — bare numbers default to days, so `today-30` and `today-30d` are equivalent.
const RELATIVE_DATE_RE = /^today(?:-(\d+)([dwy])?)?$/

function startOfLocalDay(d: Date): Date {
  const out = new Date(d)
  out.setHours(0, 0, 0, 0)
  return out
}

function buildDayLocal(y: number, m: number, d: number): Date | null {
  if (m < 1 || m > 12 || d < 1 || d > 31) return null
  const out = new Date(y, m - 1, d)
  // Reject calendar overflow (e.g. Feb 30 → Mar 2): the constructed Date
  // mis-matches the requested fields.
  if (out.getFullYear() !== y || out.getMonth() !== m - 1 || out.getDate() !== d) return null
  return out
}

function resolveDateLiteral(literal: string, now: Date): Date | null {
  const iso = ISO_DATE_RE.exec(literal)
  if (iso) return buildDayLocal(Number(iso[1]), Number(iso[2]), Number(iso[3]))
  const dmy = DMY_DATE_RE.exec(literal)
  if (dmy) return buildDayLocal(Number(dmy[3]), Number(dmy[2]), Number(dmy[1]))
  const rel = RELATIVE_DATE_RE.exec(literal)
  if (rel) {
    const amount = rel[1] ? Number(rel[1]) : 0
    const unit = rel[2] ?? 'd' // bare `today-N` defaults to days
    const out = startOfLocalDay(now)
    if (unit === 'w') {
      out.setDate(out.getDate() - amount * 7)
    } else if (unit === 'y') {
      out.setFullYear(out.getFullYear() - amount)
    } else {
      out.setDate(out.getDate() - amount)
    }
    return out
  }
  return null
}

export function parseCreatedValue(raw: string, now: Date = new Date()): ParsedCreated | null {
  let op: DateOp = 'eq'
  let rest = raw
  if (rest.startsWith('>')) {
    op = 'gt'
    rest = rest.slice(1)
  } else if (rest.startsWith('<')) {
    op = 'lt'
    rest = rest.slice(1)
  }
  const day = resolveDateLiteral(rest, now)
  if (!day) return null
  return { op, day }
}

export function matchesCreated(createdAt: Date, parsed: ParsedCreated): boolean {
  const dayStart = startOfLocalDay(parsed.day).getTime()
  const next = startOfLocalDay(parsed.day)
  next.setDate(next.getDate() + 1)
  const nextDayStart = next.getTime()
  const t = createdAt.getTime()
  if (parsed.op === 'gt') return t >= nextDayStart // strictly after the day
  if (parsed.op === 'lt') return t < dayStart // strictly before the day
  return t >= dayStart && t < nextDayStart // same day
}
