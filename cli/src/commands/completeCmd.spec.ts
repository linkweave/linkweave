import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { FolderJson, TagJson } from '../api'
import { CliError } from '../errors'

const CONFIG: { server: string; apiKey?: string; defaultCollectionId?: string } = {
  server: 'https://test.example',
  apiKey: 'lw_test',
  defaultCollectionId: 'default-collection',
}

// Reassigned per test; the factories below dereference lazily, at call time.
let clients: Record<string, Record<string, ReturnType<typeof vi.fn>>>
let clientsError: Error | undefined
let cached: string[] | undefined
const writeCached = vi.fn()

vi.mock('../config', () => ({
  resolveEffectiveConfig: () => CONFIG,
  loadStoredConfig: () => undefined,
  configPath: () => '/nowhere/config.json',
}))
vi.mock('../client', () => ({
  createAuthenticatedClients: () => {
    if (clientsError) throw clientsError
    return clients
  },
}))
vi.mock('../cache', () => ({
  readCached: () => cached,
  writeCached: (...args: unknown[]) => writeCached(...args),
}))

const { runComplete } = await import('./completeCmd')

type CompletionSource = Parameters<typeof runComplete>[0]
type CompleteOptions = Parameters<typeof runComplete>[2]

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const entityInfo = {} as TagJson['entityInfo']
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function folder(id: string, name: string, parentId?: string): FolderJson {
  return { id, entityInfo, sortOrder: 0, data: { collectionId: COLLECTION_ID, name, parentId } }
}

let stdout: string
let stderr: string
let exitCode: number | undefined

beforeEach(() => {
  CONFIG.defaultCollectionId = 'default-collection'
  stdout = ''
  stderr = ''
  cached = undefined
  clientsError = undefined
  writeCached.mockClear()
  // finish() exits from the write callback, so the stub must invoke it.
  vi.spyOn(process.stdout, 'write').mockImplementation(((chunk: unknown, ...rest: unknown[]) => {
    stdout += String(chunk)
    const flushed = rest.find((arg) => typeof arg === 'function')
    if (flushed) (flushed as () => void)()
    return true
  }) as never)
  vi.spyOn(process.stderr, 'write').mockImplementation(((chunk: unknown) => {
    stderr += String(chunk)
    return true
  }) as never)
  // runComplete always exits the process; capture the code instead.
  exitCode = undefined
  vi.spyOn(process, 'exit').mockImplementation(((code?: number) => {
    exitCode = code
  }) as never)
  clients = {
    collections: {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [
          { id: COLLECTION_ID, name: 'My Links', isDefault: true, role: 'OWNER', shared: false },
          { id: 'c2', name: 'Work', isDefault: false, role: 'OWNER', shared: false },
        ],
      }),
    },
    tags: {
      apiTagsGet: vi.fn().mockResolvedValue({
        tagList: [
          { id: 't1', entityInfo, data: { collectionId: COLLECTION_ID, name: 'dev' } },
          { id: 't2', entityInfo, data: { collectionId: COLLECTION_ID, name: 'java' } },
        ],
      }),
    },
    folders: {
      apiFoldersGet: vi
        .fn()
        .mockResolvedValue({ folderList: [folder('f1', 'Dev'), folder('f2', 'Java', 'f1')] }),
    },
    auth: { apiAuthMeGet: vi.fn().mockResolvedValue({ defaultCollectionId: 'from-server' }) },
  }
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

/** Candidates as the shell would read them: one per line. */
function lines(): string[] {
  return stdout.split('\n').filter(Boolean)
}

describe('runComplete', () => {
  it('shouldPrintCollectionNamesOnePerLine', async () => {
    await runComplete('collections', undefined, {}, cmd)

    // Newline-separated so names containing spaces survive the shell.
    expect(lines()).toEqual(['My Links', 'Work'])
  })

  it('shouldFilterByPrefixCaseInsensitively', async () => {
    await runComplete('collections', 'wo', {}, cmd)

    expect(lines()).toEqual(['Work'])
  })

  it('shouldPrintFullFolderPathsSoTheyMatchTheFlagSyntax', async () => {
    await runComplete('folders', undefined, {}, cmd)

    expect(lines()).toEqual(['Dev', 'Dev/Java'])
  })

  it('shouldIgnoreSoftDeletedFolders', async () => {
    clients['folders']!['apiFoldersGet']!.mockResolvedValue({
      folderList: [folder('f1', 'Dev'), { ...folder('f9', 'Trashed'), deletedAt: new Date() }],
    })

    await runComplete('folders', undefined, {}, cmd)

    expect(lines()).toEqual(['Dev'])
  })

  it('shouldTerminateOnAFolderParentCycleInsteadOfHangingTheShell', async () => {
    // ARRANGE: server data that points at itself would otherwise loop forever.
    clients['folders']!['apiFoldersGet']!.mockResolvedValue({
      folderList: [folder('f1', 'A', 'f2'), folder('f2', 'B', 'f1')],
    })

    // ACT
    await runComplete('folders', undefined, {}, cmd)

    // ASSERT
    expect(lines()).toHaveLength(2)
  })

  it('shouldUseTheStoredDefaultCollectionForTagsWithoutAskingTheServer', async () => {
    await runComplete('tags', undefined, {}, cmd)

    expect(clients['tags']!['apiTagsGet']).toHaveBeenCalledWith(
      { collectionId: 'default-collection' },
      expect.anything(),
    )
    expect(clients['auth']!['apiAuthMeGet']).not.toHaveBeenCalled()
  })

  it('shouldScopeTagsToAnExplicitCollectionId', async () => {
    await runComplete('tags', undefined, { collection: COLLECTION_ID }, cmd)

    expect(clients['tags']!['apiTagsGet']).toHaveBeenCalledWith(
      { collectionId: COLLECTION_ID },
      expect.anything(),
    )
  })

  it('shouldResolveACollectionNamePassedByTheShell', async () => {
    await runComplete('tags', undefined, { collection: 'Work' }, cmd)

    expect(clients['tags']!['apiTagsGet']).toHaveBeenCalledWith(
      { collectionId: 'c2' },
      expect.anything(),
    )
  })

  /** Whether the mock's first call carried an AbortSignal in any argument. */
  function calledWithSignal(mock: ReturnType<typeof vi.fn>): boolean {
    return (mock.mock.calls[0] ?? []).some(
      (arg) =>
        typeof arg === 'object' && arg !== null && (arg as { signal?: unknown }).signal instanceof AbortSignal,
    )
  }

  // Every request the completion path can make must be abortable. An
  // unbounded one wedges the user's shell mid-completion, and the generated
  // scripts cannot rescue it: they redirect stderr but impose no timeout, and
  // `timeout(1)` is not present on macOS. The abort has to come from here.
  it.each<[string, CompletionSource, CompleteOptions, string, string]>([
    ['the collection list', 'collections', {}, 'collections', 'apiCollectionsGet'],
    ['collection-name resolution', 'tags', { collection: 'Work' }, 'collections', 'apiCollectionsGet'],
    ['the tag list', 'tags', { collection: COLLECTION_ID }, 'tags', 'apiTagsGet'],
    ['the folder list', 'folders', { collection: COLLECTION_ID }, 'folders', 'apiFoldersGet'],
  ])('shouldBoundEveryRequestByADeadline: %s', async (_label, source, options, client, method) => {
    // ACT
    await runComplete(source, undefined, options, cmd)

    // ASSERT
    expect(calledWithSignal(clients[client]![method]!)).toBe(true)
  })

  it('shouldBoundEveryRequestByADeadline: the default-collection lookup', async () => {
    // ARRANGE: only reached when login stored no default collection.
    delete CONFIG.defaultCollectionId

    // ACT
    await runComplete('tags', undefined, {}, cmd)

    // ASSERT
    expect(calledWithSignal(clients['auth']!['apiAuthMeGet']!)).toBe(true)
  })

  it('shouldServeFromTheCacheWithoutTouchingTheNetwork', async () => {
    cached = ['Cached One', 'Cached Two']

    await runComplete('collections', undefined, {}, cmd)

    expect(lines()).toEqual(['Cached One', 'Cached Two'])
    expect(clients['collections']!['apiCollectionsGet']).not.toHaveBeenCalled()
  })

  it('shouldCacheWhatItFetched', async () => {
    await runComplete('collections', undefined, {}, cmd)

    expect(writeCached).toHaveBeenCalledWith('https://test.example|collections|', [
      'My Links',
      'Work',
    ])
  })

  it('shouldPrintNothingAndSucceedWhenNotAuthenticated', async () => {
    // A completion helper that errors would corrupt the command line the user
    // is in the middle of typing.
    clientsError = new CliError('Not authenticated.')

    await runComplete('collections', undefined, {}, cmd)
    expect(stdout).toBe('')
    expect(exitCode).toBe(0)
  })

  it('shouldPrintNothingWhenTheRequestFails', async () => {
    clients['collections']!['apiCollectionsGet']!.mockRejectedValue(new Error('offline'))

    await runComplete('collections', undefined, {}, cmd)
    expect(stdout).toBe('')
    expect(exitCode).toBe(0)
  })

  it('shouldStaySilentAboutFailuresByDefault', async () => {
    // ARRANGE: a shape mismatch between server and generated client, which is
    // what a missed `pnpm run generate-api` looks like at runtime.
    clients['collections']!['apiCollectionsGet']!.mockRejectedValue(
      new TypeError("Cannot read properties of undefined (reading 'map')"),
    )

    // ACT
    await runComplete('collections', undefined, {}, cmd)

    // ASSERT
    expect(stderr).toBe('')
    expect(exitCode).toBe(0)
  })

  it('shouldRevealTheCauseUnderLinkweaveDebug', async () => {
    // ARRANGE: otherwise a schema regression is indistinguishable from
    // "no candidates".
    vi.stubEnv('LINKWEAVE_DEBUG', '1')
    clients['collections']!['apiCollectionsGet']!.mockRejectedValue(
      new TypeError("Cannot read properties of undefined (reading 'map')"),
    )

    // ACT
    await runComplete('collections', undefined, {}, cmd)

    // ASSERT: still no candidates and still exit 0 — only the diagnosis changes.
    expect(stderr).toContain("Cannot read properties of undefined (reading 'map')")
    expect(stdout).toBe('')
    expect(exitCode).toBe(0)
  })

  it('shouldPrintNothingWhenNothingMatchesThePrefix', async () => {
    await runComplete('collections', 'zzz', {}, cmd)

    expect(stdout).toBe('')
  })
})
