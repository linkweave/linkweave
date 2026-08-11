import {
  chmodSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CliError } from './errors'
import {
  API_KEY_PATTERN,
  DEFAULT_SERVER,
  cacheDir,
  configDir,
  configPath,
  deleteStoredConfig,
  loadStoredConfig,
  normalizeServer,
  resolveEffectiveConfig,
  saveStoredConfig,
  updateStoredDefaultCollection,
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
  it('shouldStripTrailingSlashesAndSurroundingWhitespace', () => {
    expect(normalizeServer('https://x.example///')).toBe('https://x.example')
    expect(normalizeServer('https://x.example')).toBe('https://x.example')
    expect(normalizeServer('  http://localhost:8443  ')).toBe('http://localhost:8443')
  })

  it.each([
    ['not a URL at all', 'not-a-url'],
    ['a host:port without a scheme', 'localhost:8443'],
    ['a non-http scheme', 'ftp://x.example'],
    ['an empty string', ''],
  ])('shouldRejectAsUsageError: %s', (_label, value) => {
    // ACT
    const act = (): string => normalizeServer(value)

    // ASSERT: caught early, instead of surfacing as `TypeError: Invalid URL`
    // from inside the generated client at request time.
    expect(act).toThrow(/Invalid server URL/)
    try {
      act()
    } catch (e) {
      expect((e as CliError).exitCode).toBe(2)
    }
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

  it('shouldCarryTheInsecureFlagThrough', () => {
    expect(resolveEffectiveConfig({ insecure: true }, {}, undefined).insecure).toBe(true)
    expect(resolveEffectiveConfig({}, {}, undefined).insecure).toBe(false)
  })

  it('shouldHonourAStoredInsecureForTheSameServer', () => {
    // The completion scripts cannot pass flags, so this is the only way a
    // self-signed dev server is reachable from a <TAB>.
    const stored = { ...STORED, insecure: true }

    expect(resolveEffectiveConfig({}, {}, stored).insecure).toBe(true)
  })

  it('shouldKeepStoredInsecureEvenWhenAnotherKeyIsUsed', () => {
    // Trusting a certificate is a property of the host, not of the caller.
    const stored = { ...STORED, insecure: true }

    expect(resolveEffectiveConfig({}, { LINKWEAVE_API_KEY: KEY_B }, stored).insecure).toBe(true)
  })

  it('shouldNotLeakStoredInsecureToADifferentServer', () => {
    // ARRANGE: the stored opt-out was for the dev box, not for production.
    const stored = { ...STORED, insecure: true }

    // ACT
    const config = resolveEffectiveConfig({ server: 'https://other.example' }, {}, stored)

    // ASSERT
    expect(config.insecure).toBe(false)
  })

  it('shouldUseDefaultServerWithoutAnySource', () => {
    // `stored` is a required parameter precisely so that this `undefined`
    // means "there is no stored config" and cannot fall back to reading the
    // caller's real ~/.config/linkweave/config.json.
    const config = resolveEffectiveConfig({}, {}, undefined)
    expect(config.server).toBe(DEFAULT_SERVER)
    expect(config.apiKey).toBeUndefined()
  })

  it('shouldNotFallBackToAConfigOnDiskWhenToldThereIsNone', () => {
    // ARRANGE: a config that a defaulted `stored` parameter would have picked
    // up. That is not hypothetical — it made this suite pass or fail depending
    // on whether whoever ran it happened to be logged in, and in production it
    // made shell completion re-read the file with the stderr warning it
    // deliberately suppresses.
    const home = mkdtempSync(join(tmpdir(), 'linkweave-home-'))
    try {
      mkdirSync(join(home, '.config', 'linkweave'), { recursive: true })
      writeFileSync(join(home, '.config', 'linkweave', 'config.json'), JSON.stringify(STORED))
      vi.stubEnv('HOME', home)
      vi.stubEnv('XDG_CONFIG_HOME', join(home, '.config'))

      // ACT
      const config = resolveEffectiveConfig({}, {}, undefined)

      // ASSERT
      expect(config.apiKey).toBeUndefined()
      expect(config.server).toBe(DEFAULT_SERVER)
    } finally {
      vi.unstubAllEnvs()
      rmSync(home, { recursive: true, force: true })
    }
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

  it('shouldNotWriteTheSecretThroughAGuessableTempPath', () => {
    // ARRANGE: the temp name used to be `.config.json.<pid>.tmp`. Anyone able
    // to create files in the directory could pre-create that path — writeFile
    // ignores `mode` for an existing file, so the key landed in a file they
    // owned, or through a symlink they chose.
    const guessable = join(dir, `.config.json.${process.pid}.tmp`)
    writeFileSync(guessable, 'planted', { mode: 0o666 })

    // ACT
    saveStoredConfig(STORED, path)

    // ASSERT
    expect(readFileSync(guessable, 'utf-8')).toBe('planted')
    expect(loadStoredConfig(path)).toEqual(STORED)
  })

  it('shouldLeaveNoTemporaryFileBehind', () => {
    saveStoredConfig(STORED, path)

    expect(readdirSync(dir)).toEqual(['config.json'])
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
    // Ignored rather than fatal: normalizeServer would throw on this, which
    // would take down `linkweave login` — the only way to repair the file.
    ['an unusable server URL', JSON.stringify({ ...STORED, server: 'localhost:8443' })],
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

describe('XDG base directories', () => {
  const HOME = '/home/tester'

  it('shouldPreferXdgEnvironmentVariables', () => {
    expect(configDir({ XDG_CONFIG_HOME: '/xdg/cfg' })).toBe('/xdg/cfg/linkweave')
    expect(cacheDir({ XDG_CACHE_HOME: '/xdg/cache' })).toBe('/xdg/cache/linkweave')
  })

  it('shouldIgnoreRelativeXdgValuesAsTheSpecRequires', () => {
    // "If an implementation encounters a relative path it must be ignored."
    const withRelative = configDir({ XDG_CONFIG_HOME: 'relative/path', HOME })

    expect(withRelative).not.toContain('relative/path')
    expect(withRelative).toBe(join(HOME, '.config', 'linkweave'))
  })

  it('shouldFallBackToTheXdgDefaults', () => {
    expect(configDir({ HOME })).toBe(join(HOME, '.config', 'linkweave'))
    expect(cacheDir({ HOME })).toBe(join(HOME, '.cache', 'linkweave'))
  })

  it('shouldKeepConfigAndCacheApart', () => {
    // The completion cache is derived data and must not sit next to the key.
    expect(cacheDir({ HOME })).not.toBe(configDir({ HOME }))
  })

  it('shouldPutTheConfigFileInsideTheConfigDir', () => {
    expect(configPath({ HOME })).toBe(join(HOME, '.config', 'linkweave', 'config.json'))
  })
})

describe('updateStoredDefaultCollection', () => {
  let dir: string
  let path: string

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'linkweave-default-'))
    path = join(dir, 'config.json')
  })

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true })
  })

  it('shouldRecordTheNewDefaultForTheStoredIdentity', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)

    // ACT
    const written = updateStoredDefaultCollection(
      { server: 'https://stored.example', apiKey: KEY_A },
      'new-collection',
      path,
    )

    // ASSERT
    expect(written).toBe(true)
    expect(loadStoredConfig(path)?.defaultCollectionId).toBe('new-collection')
  })

  it('shouldLeaveEveryOtherStoredFieldAlone', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)

    // ACT
    updateStoredDefaultCollection(
      { server: 'https://stored.example', apiKey: KEY_A },
      'new-collection',
      path,
    )

    // ASSERT
    expect(loadStoredConfig(path)).toEqual({ ...STORED, defaultCollectionId: 'new-collection' })
  })

  it('shouldNotWriteForAKeyThatIsNotTheStoredOne', () => {
    // ARRANGE: a key from --api-key or the environment may belong to another
    // user, whose default is not ours to record.
    saveStoredConfig(STORED, path)

    // ACT
    const written = updateStoredDefaultCollection(
      { server: 'https://stored.example', apiKey: KEY_B },
      'new-collection',
      path,
    )

    // ASSERT
    expect(written).toBe(false)
    expect(loadStoredConfig(path)?.defaultCollectionId).toBe('stored-collection')
  })

  it('shouldNotWriteWhenPointedAtADifferentServer', () => {
    // ARRANGE
    saveStoredConfig(STORED, path)

    // ACT
    const written = updateStoredDefaultCollection(
      { server: 'https://other.example', apiKey: KEY_A },
      'new-collection',
      path,
    )

    // ASSERT
    expect(written).toBe(false)
  })

  it('shouldNotRewriteAFileThatAlreadySaysTheSameThing', () => {
    // ARRANGE: re-running the command should not touch the file at all.
    saveStoredConfig(STORED, path)

    // ACT
    const written = updateStoredDefaultCollection(
      { server: 'https://stored.example', apiKey: KEY_A },
      'stored-collection',
      path,
    )

    // ASSERT
    expect(written).toBe(false)
  })

  it('shouldDoNothingWhenThereIsNoStoredConfig', () => {
    // Credentials came from the environment; there is no file to update.
    const written = updateStoredDefaultCollection(
      { server: 'https://stored.example', apiKey: KEY_A },
      'new-collection',
      path,
    )

    expect(written).toBe(false)
    expect(existsSync(path)).toBe(false)
  })
})
