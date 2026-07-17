import { chmodSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  API_KEY_PATTERN,
  DEFAULT_SERVER,
  deleteStoredConfig,
  loadStoredConfig,
  normalizeServer,
  resolveEffectiveConfig,
  saveStoredConfig,
  type StoredConfig,
} from './config'

const KEY_A = 'lw_' + 'a'.repeat(64)
const KEY_B = 'lw_' + 'b'.repeat(64)

const STORED: StoredConfig = {
  server: 'https://stored.example',
  apiKey: KEY_A,
  userEmail: 'stored@example.com',
  defaultCollectionId: 'stored-collection',
}

describe('API_KEY_PATTERN', () => {
  it('shouldAcceptWellFormedKeys', () => {
    expect(API_KEY_PATTERN.test(KEY_A)).toBe(true)
  })

  it('shouldRejectMalformedKeys', () => {
    expect(API_KEY_PATTERN.test('lw_' + 'a'.repeat(63))).toBe(false)
    expect(API_KEY_PATTERN.test('xx_' + 'a'.repeat(64))).toBe(false)
    expect(API_KEY_PATTERN.test('lw_' + 'G'.repeat(64))).toBe(false)
    expect(API_KEY_PATTERN.test('')).toBe(false)
  })
})

describe('normalizeServer', () => {
  it('shouldStripTrailingSlashes', () => {
    expect(normalizeServer('https://x.example///')).toBe('https://x.example')
    expect(normalizeServer('https://x.example')).toBe('https://x.example')
  })
})

describe('resolveEffectiveConfig', () => {
  it('shouldPreferFlagsOverEnvAndStoredConfig', () => {
    const config = resolveEffectiveConfig(
      { server: 'https://flag.example/', apiKey: KEY_B },
      { LINKWEAVE_API_KEY: 'lw_env', LINKWEAVE_SERVER: 'https://env.example' },
      STORED,
    )
    expect(config.server).toBe('https://flag.example')
    expect(config.apiKey).toBe(KEY_B)
  })

  it('shouldPreferEnvOverStoredConfig', () => {
    const config = resolveEffectiveConfig(
      {},
      { LINKWEAVE_API_KEY: KEY_B, LINKWEAVE_SERVER: 'https://env.example' },
      STORED,
    )
    expect(config.server).toBe('https://env.example')
    expect(config.apiKey).toBe(KEY_B)
  })

  it('shouldFallBackToStoredConfigAndKeepItsIdentity', () => {
    const config = resolveEffectiveConfig({}, {}, STORED)
    expect(config.server).toBe('https://stored.example')
    expect(config.apiKey).toBe(KEY_A)
    expect(config.userEmail).toBe('stored@example.com')
    expect(config.defaultCollectionId).toBe('stored-collection')
  })

  it('shouldDropStoredIdentityWhenAnotherKeyIsInjected', () => {
    const config = resolveEffectiveConfig({}, { LINKWEAVE_API_KEY: KEY_B }, STORED)
    expect(config.userEmail).toBeUndefined()
    expect(config.defaultCollectionId).toBeUndefined()
  })

  it('shouldUseDefaultServerWithoutAnySource', () => {
    const config = resolveEffectiveConfig({}, {}, undefined)
    expect(config.server).toBe(DEFAULT_SERVER)
    expect(config.apiKey).toBeUndefined()
  })
})

describe('stored config file handling', () => {
  let dir: string
  let path: string

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'linkweave-cli-test-'))
    path = join(dir, 'config.json')
  })

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true })
  })

  it('shouldRoundTripTheConfig', () => {
    saveStoredConfig(STORED, path)
    expect(loadStoredConfig(path)).toEqual(STORED)
  })

  it('shouldWriteOwnerOnlyPermissions', () => {
    saveStoredConfig(STORED, path)
    expect(statSync(path).mode & 0o777).toBe(0o600)
  })

  it('shouldKeepPermissionsWhenOverwritingAnExistingFile', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)

    // ACT
    saveStoredConfig({ ...STORED, apiKey: KEY_B }, path)

    // ASSERT
    expect(statSync(path).mode & 0o777).toBe(0o600)
    expect(JSON.parse(readFileSync(path, 'utf-8')).apiKey).toBe(KEY_B)
  })

  it('shouldRestoreOwnerOnlyPermissionsWhenOverwritingALooserFile', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)
    chmodSync(path, 0o644)

    // ACT
    saveStoredConfig({ ...STORED, apiKey: KEY_B }, path)

    // ASSERT
    expect(statSync(path).mode & 0o777).toBe(0o600)
  })

  it('shouldReturnUndefinedWhenFileIsMissing', () => {
    expect(loadStoredConfig(path)).toBeUndefined()
  })

  it('shouldWarnAndIgnoreCorruptJsonSoLoginStaysUsable', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)
    writeFileSync(path, 'not json')
    const stderrSpy = vi.spyOn(process.stderr, 'write').mockReturnValue(true)

    // ACT
    const loaded = loadStoredConfig(path)

    // ASSERT
    expect(loaded).toBeUndefined()
    expect(stderrSpy).toHaveBeenCalledWith(expect.stringContaining('not valid JSON'))
    stderrSpy.mockRestore()
  })

  it.each([
    ['an empty object', '{}'],
    ['a null literal', 'null'],
    ['a non-object', '"lw_key"'],
    ['missing apiKey', '{ "server": "https://x.example" }'],
    ['a non-string server', `{ "server": 42, "apiKey": "${KEY_A}" }`],
    ['a non-string optional field', JSON.stringify({ ...STORED, userEmail: 7 })],
  ])('shouldWarnAndIgnoreValidJsonWithBadShape: %s', (_label, content) => {
    // ARRANGE
    writeFileSync(path, content)
    const stderrSpy = vi.spyOn(process.stderr, 'write').mockReturnValue(true)

    // ACT
    const loaded = loadStoredConfig(path)

    // ASSERT
    expect(loaded).toBeUndefined()
    expect(stderrSpy).toHaveBeenCalledWith(expect.stringContaining('missing or malformed fields'))
    stderrSpy.mockRestore()
  })

  it('shouldLoadConfigsCarryingUnknownExtraFields', () => {
    // ARRANGE: a config written by a newer CLI version must stay readable.
    writeFileSync(path, JSON.stringify({ ...STORED, futureField: true }))

    // ACT
    const loaded = loadStoredConfig(path)

    // ASSERT
    expect(loaded).toMatchObject(STORED)
  })

  it('shouldDeleteTheConfigFile', () => {
    saveStoredConfig(STORED, path)
    expect(deleteStoredConfig(path)).toBe(true)
    expect(deleteStoredConfig(path)).toBe(false)
  })
})
