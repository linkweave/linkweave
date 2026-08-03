import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { readCached, writeCached } from './cache'

const NOW = 1_000_000

describe('completion cache', () => {
  let dir: string
  let path: string

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'linkweave-cache-test-'))
    path = join(dir, 'completion-cache.json')
  })

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true })
  })

  it('shouldRoundTripValuesWithinTheTtl', () => {
    writeCached('key', ['Work', 'My Links'], path, NOW)

    expect(readCached('key', path, NOW + 1_000)).toEqual(['Work', 'My Links'])
  })

  it('shouldTreatExpiredEntriesAsAbsent', () => {
    writeCached('key', ['Work'], path, NOW)

    expect(readCached('key', path, NOW + 120_000)).toBeUndefined()
  })

  it('shouldReturnUndefinedForAnUnknownKeyAndAMissingFile', () => {
    expect(readCached('nope', path, NOW)).toBeUndefined()
    writeCached('key', ['Work'], path, NOW)
    expect(readCached('other', path, NOW)).toBeUndefined()
  })

  it('shouldWriteOwnerOnlyPermissions', () => {
    // The cache holds the user's collection, tag and folder names.
    writeCached('key', ['Work'], path, NOW)

    expect(statSync(path).mode & 0o777).toBe(0o600)
  })

  it('shouldPruneExpiredEntriesOnWriteSoTheFileCannotGrowForever', () => {
    // ARRANGE
    writeCached('old', ['gone'], path, NOW)

    // ACT
    writeCached('fresh', ['kept'], path, NOW + 120_000)

    // ASSERT
    const stored = JSON.parse(readFileSync(path, 'utf-8'))
    expect(Object.keys(stored)).toEqual(['fresh'])
  })

  it.each([
    ['invalid JSON', 'not json'],
    ['a non-object', '"scalar"'],
    ['an entry of the wrong shape', '{"key":{"expiresAt":"soon","values":null}}'],
  ])('shouldIgnoreACorruptCacheRatherThanThrow: %s', (_label, content) => {
    // ARRANGE
    writeFileSync(path, content)

    // ASSERT: a damaged cache is an empty cache, and stays writable.
    expect(readCached('key', path, NOW)).toBeUndefined()
    expect(() => writeCached('key', ['Work'], path, NOW)).not.toThrow()
    expect(readCached('key', path, NOW)).toEqual(['Work'])
  })

  it('shouldNotThrowWhenTheCacheCannotBeWritten', () => {
    // ARRANGE: a path whose parent is a file, so mkdir/rename cannot succeed.
    const blocked = join(path, 'nested', 'cache.json')
    writeFileSync(path, '{}')

    // ASSERT: completion must survive an unwritable cache.
    expect(() => writeCached('key', ['Work'], blocked, NOW)).not.toThrow()
  })
})
