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

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const entityInfo = {} as TagJson['entityInfo']
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function folder(id: string, name: string, parentId?: string): FolderJson {
  return { id, entityInfo, sortOrder: 0, data: { collectionId: COLLECTION_ID, name, parentId } }
}

let stdout: string

beforeEach(() => {
  stdout = ''
  cached = undefined
  clientsError = undefined
  writeCached.mockClear()
  vi.spyOn(process.stdout, 'write').mockImplementation((chunk) => {
    stdout += String(chunk)
    return true
  })
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

    await expect(runComplete('collections', undefined, {}, cmd)).resolves.toBeUndefined()
    expect(stdout).toBe('')
  })

  it('shouldPrintNothingWhenTheRequestFails', async () => {
    clients['collections']!['apiCollectionsGet']!.mockRejectedValue(new Error('offline'))

    await expect(runComplete('collections', undefined, {}, cmd)).resolves.toBeUndefined()
    expect(stdout).toBe('')
  })

  it('shouldPrintNothingWhenNothingMatchesThePrefix', async () => {
    await runComplete('collections', 'zzz', {}, cmd)

    expect(stdout).toBe('')
  })
})
