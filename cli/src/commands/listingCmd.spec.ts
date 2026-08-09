import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CollectionSummaryJson, FolderJson, TagJson } from '../api'

const CONFIG = {
  server: 'https://test.example',
  apiKey: 'lw_test',
  defaultCollectionId: 'default-collection',
}

let clients: Record<string, Record<string, ReturnType<typeof vi.fn>>>

vi.mock('../config', () => ({
  resolveEffectiveConfig: () => CONFIG,
  loadStoredConfig: () => undefined,
}))
vi.mock('../client', () => ({ createAuthenticatedClients: () => clients }))

const { runTagsList } = await import('./tagsCmd')
const { runFoldersList } = await import('./foldersCmd')
const { runCollectionsList } = await import('./collectionsCmd')

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const entityInfo = {} as TagJson['entityInfo']
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function tag(id: string, name: string): TagJson {
  return { id, entityInfo, data: { collectionId: COLLECTION_ID, name } }
}

function folder(id: string, name: string, parentId?: string): FolderJson {
  return { id, entityInfo, sortOrder: 0, data: { collectionId: COLLECTION_ID, name, parentId } }
}

function collection(
  id: string,
  name: string,
  extras: Partial<CollectionSummaryJson> = {},
): CollectionSummaryJson {
  return {
    id,
    name,
    isDefault: false,
    role: 'OWNER' as CollectionSummaryJson['role'],
    shared: false,
    ...extras,
  }
}

let stdout: string

beforeEach(() => {
  stdout = ''
  vi.spyOn(console, 'log').mockImplementation((...args: unknown[]) => {
    stdout += args.join(' ') + '\n'
  })
  clients = {
    tags: {
      // Deliberately not alphabetical: the server returns creation order.
      apiTagsGet: vi.fn().mockResolvedValue({
        tagList: [tag('t2', 'vue'), tag('t1', 'dev'), tag('t3', 'Java')],
      }),
    },
    folders: {
      apiFoldersGet: vi.fn().mockResolvedValue({
        folderList: [
          folder('f2', 'TypeScript', 'f1'),
          // In the middle on purpose: with the deleted folder last, a paths[]
          // that had been filtered separately would still line up by accident.
          { ...folder('f9', 'Trashed'), deletedAt: new Date() },
          folder('f1', 'Dev'),
          folder('f3', 'Ops'),
        ],
      }),
    },
    collections: {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [
          collection('c1', 'Personal', { isDefault: true }),
          collection('c2', 'Team Reading', { role: 'MEMBER', shared: true }),
        ],
      }),
    },
    auth: { apiAuthMeGet: vi.fn() },
  }
})

afterEach(() => {
  vi.restoreAllMocks()
})

function lines(): string[] {
  return stdout.split('\n').filter(Boolean)
}

describe('runTagsList', () => {
  it('shouldListTagsAlphabeticallyRatherThanInCreationOrder', async () => {
    await runTagsList({ format: 'ids' }, cmd)

    // dev, Java, vue — localeCompare folds case, so an uppercase tag is not
    // banished to the top the way a plain `<` comparison would put it.
    expect(lines()).toEqual(['t1', 't3', 't2'])
  })

  it('shouldRenderIdAndNameAsATable', async () => {
    await runTagsList({ format: 'table' }, cmd)

    expect(lines()[0]).toMatch(/^ID\s+Name$/)
    expect(stdout).toContain('t1  dev')
  })

  it('shouldEmitJsonWithoutTheEnvelopeFields', async () => {
    await runTagsList({ format: 'json' }, cmd)

    expect(JSON.parse(stdout)).toEqual([
      { id: 't1', name: 'dev' },
      { id: 't3', name: 'Java' },
      { id: 't2', name: 'vue' },
    ])
  })

  it('shouldUseTheStoredDefaultCollection', async () => {
    await runTagsList({ format: 'ids' }, cmd)

    expect(clients['tags']!['apiTagsGet']).toHaveBeenCalledWith({
      collectionId: 'default-collection',
    })
  })
})

describe('runFoldersList', () => {
  it('shouldShowFullPathsSortedSoChildrenFollowTheirParent', async () => {
    await runFoldersList({ format: 'json' }, cmd)

    expect(JSON.parse(stdout)).toEqual([
      { id: 'f1', path: 'Dev' },
      { id: 'f2', path: 'Dev/TypeScript' },
      { id: 'f3', path: 'Ops' },
    ])
  })

  it('shouldOmitTrashedFolders', async () => {
    // They belong to `trash list`, and `--folder` would not accept them.
    await runFoldersList({ format: 'ids' }, cmd)

    expect(lines()).not.toContain('f9')
  })

  it('shouldRenderPathsAsATable', async () => {
    await runFoldersList({ format: 'table' }, cmd)

    expect(lines()[0]).toMatch(/^ID\s+Path$/)
    expect(stdout).toContain('Dev/TypeScript')
  })
})

describe('runCollectionsList', () => {
  it('shouldPrintOneIdPerLineForIdsFormat', async () => {
    await runCollectionsList({ format: 'ids' }, cmd)

    expect(lines()).toEqual(['c1', 'c2'])
  })

  it('shouldDefaultToTableWhenNoFormatIsGiven', async () => {
    // The option carries a commander default, but the handler must not fall
    // over when called without one.
    await runCollectionsList({}, cmd)

    expect(lines()[0]).toMatch(/^ID\s+Name\s+Default\s+Role\s+Shared$/)
  })

  it('shouldMarkTheDefaultAndSharedFlagsAsYesOrBlank', async () => {
    // ARRANGE: a tick-per-row would misalign; blank reads as "no" in a table.

    // ACT
    await runCollectionsList({ format: 'table' }, cmd)

    // ASSERT
    const [, , personal, team] = lines()
    expect(personal).toMatch(/c1\s+Personal\s+yes\s+OWNER$/)
    expect(team).toMatch(/c2\s+Team Reading\s+MEMBER\s+yes$/)
  })

  it('shouldEmitJsonWithoutTheEnvelopeFields', async () => {
    await runCollectionsList({ format: 'json' }, cmd)

    expect(JSON.parse(stdout)).toEqual([
      { id: 'c1', name: 'Personal', isDefault: true, role: 'OWNER', shared: false },
      { id: 'c2', name: 'Team Reading', isDefault: false, role: 'MEMBER', shared: true },
    ])
  })

  it('shouldRejectAnUnknownFormatBeforeCallingTheApi', async () => {
    // ARRANGE: parseFormat guards the handler even when commander's choices()
    // is bypassed — a usage error should not cost a round trip.

    // ACT & ASSERT
    await expect(runCollectionsList({ format: 'yaml' }, cmd)).rejects.toThrow(/Invalid format/)
    expect(clients['collections']!['apiCollectionsGet']).not.toHaveBeenCalled()
  })
})
