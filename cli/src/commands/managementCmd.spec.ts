import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CollectionSummaryJson, FolderJson, TagJson } from '../api'
import { EXIT_USAGE } from '../errors'

const CONFIG = {
  server: 'https://test.example',
  apiKey: 'lw_test',
  defaultCollectionId: 'default-collection',
}

let clients: Record<string, Record<string, ReturnType<typeof vi.fn>>>

const updateStoredDefaultCollection = vi.fn()
const createInterface = vi.fn()

vi.mock('../config', () => ({
  resolveEffectiveConfig: () => CONFIG,
  loadStoredConfig: () => undefined,
  updateStoredDefaultCollection,
}))
vi.mock('../client', () => ({ createAuthenticatedClients: () => clients }))
vi.mock('node:readline/promises', () => ({ createInterface }))

const { runCollectionsCreate, runCollectionsRename, runCollectionsRm, runCollectionsSetDefault } =
  await import('./collectionsCmd')
const { runTagsRename, runTagsRm } = await import('./tagsCmd')
const { runFoldersCreate, runFoldersMv, runFoldersRename, runFoldersRm } =
  await import('./foldersCmd')

const COLLECTION_ID = 'default-collection'
// Only a UUID-shaped spec is treated as an ID; anything else is matched by
// name. One fixture therefore has to carry a real UUID.
const JAVA_TAG_ID = '550e8400-e29b-41d4-a716-446655440001'
const entityInfo = {} as TagJson['entityInfo']
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function tag(id: string, name: string, color?: string): TagJson {
  return { id, entityInfo, data: { collectionId: COLLECTION_ID, name, color } }
}

function folder(
  id: string,
  name: string,
  parentId?: string,
  color?: string,
): FolderJson {
  return {
    id,
    entityInfo,
    sortOrder: 0,
    data: { collectionId: COLLECTION_ID, name, parentId, color },
  }
}

function collection(
  id: string,
  name: string,
  role: CollectionSummaryJson['role'] = 'OWNER' as CollectionSummaryJson['role'],
): CollectionSummaryJson {
  return { id, name, isDefault: false, role, shared: false }
}

let stdout: string

beforeEach(() => {
  stdout = ''
  vi.clearAllMocks()
  // Answering "y" by default; the tests that care set their own answer.
  createInterface.mockReturnValue({ question: vi.fn().mockResolvedValue('y'), close: vi.fn() })
  process.stdin.isTTY = true
  vi.spyOn(console, 'log').mockImplementation((...args: unknown[]) => {
    stdout += args.join(' ') + '\n'
  })
  clients = {
    collections: {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [collection('c1', 'Personal'), collection('c2', 'Work')],
      }),
      apiCollectionsPost: vi.fn().mockResolvedValue(collection('c3', 'Archive')),
      apiCollectionsIdGet: vi.fn().mockResolvedValue({
        id: 'c2',
        name: 'Work',
        browserFetchAllowlist: 'example.com',
        screenshotEnabled: true,
        bookmarks: [],
        tags: [],
        folders: [],
        autoTagRules: [],
        propertyDefinitions: [],
      }),
      apiCollectionsIdPut: vi.fn().mockResolvedValue(collection('c2', 'Werk')),
      apiCollectionsIdDelete: vi.fn().mockResolvedValue(undefined),
      apiCollectionsIdDefaultPut: vi.fn().mockResolvedValue(undefined),
    },
    tags: {
      apiTagsGet: vi.fn().mockResolvedValue({
        tagList: [tag('t1', 'dev', '#ff0000'), tag(JAVA_TAG_ID, 'java')],
      }),
      apiTagsTagIdPut: vi.fn().mockResolvedValue(tag('t1', 'devops')),
      apiTagsTagIdDelete: vi.fn().mockResolvedValue(undefined),
    },
    folders: {
      apiFoldersGet: vi.fn().mockResolvedValue({
        folderList: [
          folder('f1', 'Dev'),
          folder('f2', 'TypeScript', 'f1', '#00ff00'),
          folder('f3', 'Ops'),
        ],
      }),
      apiFoldersPost: vi.fn().mockImplementation(({ folderSaveJson }) =>
        Promise.resolve(folder('new', folderSaveJson.name, folderSaveJson.parentId)),
      ),
      apiFoldersFolderIdPut: vi.fn().mockResolvedValue(folder('f2', 'TS', 'f1')),
      apiFoldersFolderIdMovePatch: vi.fn().mockResolvedValue(folder('f2', 'TypeScript', 'f3')),
      apiFoldersFolderIdDelete: vi.fn().mockResolvedValue(undefined),
    },
    auth: { apiAuthMeGet: vi.fn().mockResolvedValue({ defaultCollectionId: COLLECTION_ID }) },
  }
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('collections create', () => {
  it('shouldPostTheNameAndReportTheNewId', async () => {
    // ACT
    await runCollectionsCreate('Archive', {}, cmd)

    // ASSERT
    expect(clients['collections']!['apiCollectionsPost']).toHaveBeenCalledWith({
      collectionCreateJson: { name: 'Archive' },
    })
    expect(stdout).toContain('✓ Collection created: Archive (c3)')
  })
})

describe('collections rename', () => {
  it('shouldPreserveSettingsTheCliDoesNotExpose', async () => {
    // ARRANGE: the update endpoint replaces the whole collection, so the
    // screenshot toggle and fetch allowlist have to be read back and resent —
    // otherwise a rename silently resets them.

    // ACT
    await runCollectionsRename('Work', 'Werk', {}, cmd)

    // ASSERT
    expect(clients['collections']!['apiCollectionsIdPut']).toHaveBeenCalledWith({
      id: 'c2',
      collectionUpdateJson: {
        name: 'Werk',
        browserFetchAllowlist: 'example.com',
        screenshotEnabled: true,
      },
    })
  })

  it('shouldReportBothNames', async () => {
    await runCollectionsRename('Work', 'Werk', {}, cmd)

    expect(stdout).toContain('✓ Collection renamed: Work → Werk')
  })

  it('shouldFailOnAnUnknownCollection', async () => {
    await expect(runCollectionsRename('Nope', 'X', {}, cmd)).rejects.toThrow(/No collection found/)
    expect(clients['collections']!['apiCollectionsIdPut']).not.toHaveBeenCalled()
  })

  it('shouldRefuseWhenTheCallerIsNotTheOwner', async () => {
    // ARRANGE: renaming is owner-only and the server enforces it by ignoring
    // the new name rather than refusing — an admin's rename returns 200 as if
    // it had worked, so the role has to stop it before anything is sent.
    clients['collections']!['apiCollectionsGet']!.mockResolvedValue({
      collections: [collection('c2', 'Work', 'ADMIN' as CollectionSummaryJson['role'])],
    })

    // ACT & ASSERT
    await expect(runCollectionsRename('Work', 'Werk', {}, cmd)).rejects.toThrow(
      /renaming 'Work' is restricted to its owner/,
    )
    expect(clients['collections']!['apiCollectionsIdPut']).not.toHaveBeenCalled()
    expect(stdout).toBe('')
  })

  it('shouldNotBlameOwnershipWhenAnOwnerRenamesToTheCurrentName', async () => {
    // ARRANGE: the name also comes back unchanged here, and nothing is wrong —
    // the echo is why the role, not the response, decides the failure.
    clients['collections']!['apiCollectionsIdPut']!.mockResolvedValue(collection('c2', 'Work'))

    // ACT
    await runCollectionsRename('Work', 'Work', {}, cmd)

    // ASSERT
    expect(stdout).toContain('✓ Collection renamed: Work → Work')
  })

  it('shouldReportTheNameTheServerActuallyStored', async () => {
    // ARRANGE: not the one that was requested.
    clients['collections']!['apiCollectionsIdPut']!.mockResolvedValue(collection('c2', 'Werk '))

    // ACT
    await runCollectionsRename('Work', 'Werk', {}, cmd)

    // ASSERT
    expect(stdout).toContain('✓ Collection renamed: Work → Werk ')
  })
})

describe('collections rm', () => {
  it('shouldAskBeforeDeletingAndSayNothingIsRecoverable', async () => {
    // ARRANGE
    const question = vi.fn().mockResolvedValue('y')
    createInterface.mockReturnValue({ question, close: vi.fn() })

    // ACT
    await runCollectionsRm('Work', {}, cmd)

    // ASSERT
    expect(question.mock.calls[0]![0]).toMatch(/Permanently delete collection 'Work'/)
    expect(question.mock.calls[0]![0]).toMatch(/cannot be undone/)
  })

  it('shouldNotDeleteWhenTheUserDeclines', async () => {
    // ARRANGE
    createInterface.mockReturnValue({
      question: vi.fn().mockResolvedValue('n'),
      close: vi.fn(),
    })

    // ACT & ASSERT
    await expect(runCollectionsRm('Work', {}, cmd)).rejects.toThrow('Aborted.')
    expect(clients['collections']!['apiCollectionsIdDelete']).not.toHaveBeenCalled()
  })

  it('shouldSkipThePromptWithYes', async () => {
    // ACT
    await runCollectionsRm('Work', { yes: true }, cmd)

    // ASSERT
    expect(createInterface).not.toHaveBeenCalled()
    expect(clients['collections']!['apiCollectionsIdDelete']).toHaveBeenCalledWith({ id: 'c2' })
    expect(stdout).toContain('✓ Collection deleted: Work')
  })

  it('shouldRefuseWithoutATtyAndWithoutYes', async () => {
    // ARRANGE: piped stdin is not consent for an unrecoverable delete.
    process.stdin.isTTY = false

    // ACT
    const error = await runCollectionsRm('Work', {}, cmd).catch((e: unknown) => e)

    // ASSERT
    expect((error as { exitCode: number }).exitCode).toBe(EXIT_USAGE)
    expect(clients['collections']!['apiCollectionsIdDelete']).not.toHaveBeenCalled()
  })
})

describe('collections default', () => {
  it('shouldSetTheDefaultServerSide', async () => {
    await runCollectionsSetDefault('Work', {}, cmd)

    expect(clients['collections']!['apiCollectionsIdDefaultPut']).toHaveBeenCalledWith({ id: 'c2' })
  })

  it('shouldWriteTheNewDefaultIntoTheStoredConfig', async () => {
    // ARRANGE: commands prefer the default captured at login, so without this
    // the change would look like a no-op from the next invocation onwards.

    // ACT
    await runCollectionsSetDefault('Work', {}, cmd)

    // ASSERT
    expect(updateStoredDefaultCollection).toHaveBeenCalledWith(CONFIG, 'c2')
  })
})

describe('tags rename', () => {
  it('shouldPreserveTheTagColour', async () => {
    // ARRANGE: the save payload replaces the tag outright, so a colour picked
    // in the web UI would be dropped if it were not resent.

    // ACT
    await runTagsRename('dev', 'devops', {}, cmd)

    // ASSERT
    expect(clients['tags']!['apiTagsTagIdPut']).toHaveBeenCalledWith({
      tagId: 't1',
      tagSaveJson: { collectionId: COLLECTION_ID, name: 'devops', color: '#ff0000' },
    })
  })

  it('shouldMatchTheTagNameCaseInsensitively', async () => {
    await runTagsRename('DEV', 'devops', {}, cmd)

    expect(clients['tags']!['apiTagsTagIdPut']).toHaveBeenCalledOnce()
  })

  it('shouldAcceptATagId', async () => {
    await runTagsRename(JAVA_TAG_ID, 'jvm', {}, cmd)

    expect(clients['tags']!['apiTagsTagIdPut']).toHaveBeenCalledWith(
      expect.objectContaining({ tagId: JAVA_TAG_ID }),
    )
  })

  it('shouldFailOnAnUnknownTag', async () => {
    await expect(runTagsRename('nope', 'x', {}, cmd)).rejects.toThrow(/No tag found matching/)
  })
})

describe('tags rm', () => {
  it('shouldWarnThatEveryBookmarkLosesTheTag', async () => {
    // ARRANGE
    const question = vi.fn().mockResolvedValue('y')
    createInterface.mockReturnValue({ question, close: vi.fn() })

    // ACT
    await runTagsRm('dev', {}, cmd)

    // ASSERT
    expect(question.mock.calls[0]![0]).toMatch(/remove it from every bookmark/)
  })

  it('shouldDeleteTheTagAfterConfirmation', async () => {
    await runTagsRm('dev', { yes: true }, cmd)

    expect(clients['tags']!['apiTagsTagIdDelete']).toHaveBeenCalledWith({ tagId: 't1' })
    expect(stdout).toContain('✓ Tag deleted: dev')
  })

  it('shouldNotDeleteWhenTheUserDeclines', async () => {
    createInterface.mockReturnValue({
      question: vi.fn().mockResolvedValue(''),
      close: vi.fn(),
    })

    await expect(runTagsRm('dev', {}, cmd)).rejects.toThrow('Aborted.')
    expect(clients['tags']!['apiTagsTagIdDelete']).not.toHaveBeenCalled()
  })
})

describe('folders create', () => {
  it('shouldCreateMissingParentsLikeMkdirP', async () => {
    // ACT
    await runFoldersCreate('Dev/Rust/Async', {}, cmd)

    // ASSERT — Dev exists, so only the two new segments are posted.
    const posted = clients['folders']!['apiFoldersPost']!.mock.calls.map(
      (call) => (call[0] as { folderSaveJson: { name: string } }).folderSaveJson.name,
    )
    expect(posted).toEqual(['Rust', 'Async'])
    expect(stdout).toContain('✓ Folder created: Dev/Rust/Async')
  })

  it('shouldFetchTheFolderListOnlyOnce', async () => {
    // ARRANGE: the existence check and the walk that creates the missing
    // segments need the same hierarchy, so the list is fetched once and reused.

    // ACT
    await runFoldersCreate('Dev/Rust/Async', {}, cmd)

    // ASSERT
    expect(clients['folders']!['apiFoldersGet']).toHaveBeenCalledOnce()
  })

  it('shouldRefuseToCreateAPathThatAlreadyExists', async () => {
    // ARRANGE: like mkdir without -p, a repeat almost always means a typo.

    // ACT & ASSERT
    await expect(runFoldersCreate('Dev/TypeScript', {}, cmd)).rejects.toThrow(
      /Folder already exists at path 'Dev\/TypeScript'/,
    )
    expect(clients['folders']!['apiFoldersPost']).not.toHaveBeenCalled()
  })

  it('shouldRejectAPathWithNoSegments', async () => {
    await expect(runFoldersCreate('///', {}, cmd)).rejects.toThrow(/Invalid folder path/)
  })
})

describe('folders rename', () => {
  it('shouldKeepTheFolderWhereItIsRatherThanReHomingItToTheRoot', async () => {
    // ARRANGE: the server reads an absent parentId as "move to the root", so
    // omitting it would drag the folder and its whole subtree to the top level.

    // ACT
    await runFoldersRename('Dev/TypeScript', 'TS', {}, cmd)

    // ASSERT
    expect(clients['folders']!['apiFoldersFolderIdPut']).toHaveBeenCalledWith({
      folderId: 'f2',
      folderSaveJson: {
        collectionId: COLLECTION_ID,
        parentId: 'f1',
        name: 'TS',
        color: '#00ff00',
      },
    })
  })

  it('shouldReportTheNewFullPath', async () => {
    await runFoldersRename('Dev/TypeScript', 'TS', {}, cmd)

    expect(stdout).toContain('✓ Folder renamed: Dev/TypeScript → Dev/TS')
  })

  it('shouldRejectANameContainingASlash', async () => {
    // ARRANGE: `folders rename A B/C` is ambiguous about whether a move was
    // also meant; `folders mv` is the command for that.

    // ACT & ASSERT
    await expect(runFoldersRename('Dev', 'Ops/Sub', {}, cmd)).rejects.toThrow(
      /use 'linkweave folders mv'/,
    )
    expect(clients['folders']!['apiFoldersFolderIdPut']).not.toHaveBeenCalled()
  })
})

describe('folders mv', () => {
  it('shouldReparentUnderTheDestination', async () => {
    // ACT
    await runFoldersMv('Dev/TypeScript', 'Ops', {}, cmd)

    // ASSERT
    expect(clients['folders']!['apiFoldersFolderIdMovePatch']).toHaveBeenCalledWith({
      folderId: 'f2',
      folderMoveJson: { collectionId: COLLECTION_ID, parentId: 'f3' },
    })
    expect(stdout).toContain('✓ Folder moved: Dev/TypeScript → Ops/TypeScript')
  })

  it('shouldMoveToTheTopLevelOnASlash', async () => {
    // ACT
    await runFoldersMv('Dev/TypeScript', '/', {}, cmd)

    // ASSERT — an absent parentId is what puts a folder at the root.
    expect(clients['folders']!['apiFoldersFolderIdMovePatch']).toHaveBeenCalledWith({
      folderId: 'f2',
      folderMoveJson: { collectionId: COLLECTION_ID, parentId: undefined },
    })
    expect(stdout).toContain('→ TypeScript')
  })

  it('shouldRefuseToMoveAFolderIntoItself', async () => {
    await expect(runFoldersMv('Dev', 'Dev', {}, cmd)).rejects.toThrow(/cannot be moved into itself/)
    expect(clients['folders']!['apiFoldersFolderIdMovePatch']).not.toHaveBeenCalled()
  })

  it('shouldRefuseToMoveAFolderIntoItsOwnSubfolder', async () => {
    // ARRANGE: this would orphan the subtree; the server rejects it too, but
    // without naming the folders involved.

    // ACT & ASSERT
    await expect(runFoldersMv('Dev', 'Dev/TypeScript', {}, cmd)).rejects.toThrow(
      /Cannot move 'Dev' into its own subfolder 'Dev\/TypeScript'/,
    )
    expect(clients['folders']!['apiFoldersFolderIdMovePatch']).not.toHaveBeenCalled()
  })
})

describe('folders rm', () => {
  it('shouldDeleteWithoutAPromptBecauseItIsRecoverable', async () => {
    // ARRANGE: the cascade is a soft delete, like `bookmarks rm`.

    // ACT
    await runFoldersRm('Dev/TypeScript', {}, cmd)

    // ASSERT
    expect(createInterface).not.toHaveBeenCalled()
    expect(clients['folders']!['apiFoldersFolderIdDelete']).toHaveBeenCalledWith({ folderId: 'f2' })
  })

  it('shouldSayHowToUndoIt', async () => {
    await runFoldersRm('Dev/TypeScript', {}, cmd)

    expect(stdout).toContain('✓ Folder removed: Dev/TypeScript')
    expect(stdout).toContain("linkweave trash restore f2")
  })

  it('shouldFailOnAnUnknownPath', async () => {
    await expect(runFoldersRm('Nope/Here', {}, cmd)).rejects.toThrow(
      /No folder found at path 'Nope\/Here'/,
    )
  })
})
