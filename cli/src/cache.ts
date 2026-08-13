import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { cacheDir, writePrivateFile } from './config'

/**
 * Short-lived cache for shell-completion candidates. Tab completion has to
 * feel instant, and a round trip per keypress does not — but the data is
 * cosmetic, so a stale collection name for a minute costs nothing.
 */
const TTL_MS = 60_000

function cachePath(): string {
  return join(cacheDir(), 'completion-cache.json')
}

interface CacheEntry {
  expiresAt: number
  values: string[]
}

function isEntry(value: unknown): value is CacheEntry {
  if (typeof value !== 'object' || value === null) return false
  const entry = value as Record<string, unknown>
  return (
    typeof entry['expiresAt'] === 'number' &&
    Array.isArray(entry['values']) &&
    entry['values'].every((item) => typeof item === 'string')
  )
}

/** A damaged or unreadable cache is simply an empty one — never an error. */
function readAll(path: string): Record<string, CacheEntry> {
  let parsed: unknown
  try {
    parsed = JSON.parse(readFileSync(path, 'utf-8'))
  } catch {
    return {}
  }
  if (typeof parsed !== 'object' || parsed === null) return {}
  const entries = Object.entries(parsed as Record<string, unknown>).filter(([, value]) =>
    isEntry(value),
  )
  return Object.fromEntries(entries) as Record<string, CacheEntry>
}

export function readCached(
  key: string,
  path: string = cachePath(),
  now: number = Date.now(),
): string[] | undefined {
  const entry = readAll(path)[key]
  return entry !== undefined && entry.expiresAt > now ? entry.values : undefined
}

export function writeCached(
  key: string,
  values: string[],
  path: string = cachePath(),
  now: number = Date.now(),
): void {
  try {
    const all = readAll(path)
    // Expired entries are dropped on every write so the file cannot grow
    // without bound as collections and tags come and go.
    for (const [existing, entry] of Object.entries(all)) {
      if (entry.expiresAt <= now) delete all[existing]
    }
    all[key] = { expiresAt: now + TTL_MS, values }
    // Owner-only: the cache holds the user's collection, tag and folder names.
    writePrivateFile(path, JSON.stringify(all) + '\n')
  } catch {
    // Best-effort. A cache that cannot be written must not break completion.
  }
}
