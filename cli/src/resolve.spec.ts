import { describe, expect, it, vi } from 'vitest'

import type { CollectionSummaryJson, FolderJson, TagJson } from './api'
import { CliError } from './errors'
import {
  findCollection,
  findFolder,
  findTag,
  folderPathSegments,
  folderPaths,
  looksLikeId,
  normalizeFolderPath,
  parentFolderPath,
  parseTagNames,
  resolveCollectionId,
  resolveFolderId,
  resolveTagIds,
} from './resolve'

const UUID_A = '550e8400-e29b-41d4-a716-446655440000'
const UUID_B = '660e8400-e29b-41d4-a716-446655440001'

const entityInfo = {} as TagJson['entityInfo']

function collection(id: string, name: string): CollectionSummaryJson {
  return { id, name, isDefault: false, role: 'OWNER' as CollectionSummaryJson['role'], shared: false }
}

function tag(id: string, name: string): TagJson {
  return { id, entityInfo, data: { collectionId: UUID_A, name } }
}

function folder(id: string, name: string, parentId?: string): FolderJson {
  return { id, entityInfo, sortOrder: 0, data: { collectionId: UUID_A, name, parentId } }
}

describe('looksLikeId', () => {
  it('shouldDetectUuids', () => {
    expect(looksLikeId(UUID_A)).toBe(true)
    expect(looksLikeId('my-links')).toBe(false)
  })
})

describe('resolveCollectionId', () => {
  it('shouldPassUuidsThroughWithoutServerCall', async () => {
    const api = { apiCollectionsGet: vi.fn() }
    expect(await resolveCollectionId(api, UUID_A)).toBe(UUID_A)
    expect(api.apiCollectionsGet).not.toHaveBeenCalled()
  })

  it('shouldMatchCollectionNamesCaseInsensitively', async () => {
    const api = {
      apiCollectionsGet: vi
        .fn()
        .mockResolvedValue({ collections: [collection(UUID_A, 'My Links')] }),
    }
    expect(await resolveCollectionId(api, 'my links')).toBe(UUID_A)
  })

  it('shouldFailOnAmbiguousNames', async () => {
    const api = {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [collection(UUID_A, 'Links'), collection(UUID_B, 'links')],
      }),
    }
    await expect(resolveCollectionId(api, 'links')).rejects.toThrow(/Multiple collections match/)
  })

  it('shouldForwardRequestOptionsSoCallersCanImposeADeadline', async () => {
    // ARRANGE: shell completion needs this lookup to be abortable.
    const api = {
      apiCollectionsGet: vi.fn().mockResolvedValue({ collections: [collection(UUID_A, 'Work')] }),
    }
    const signal = AbortSignal.timeout(1_000)

    // ACT
    await resolveCollectionId(api, 'Work', { signal })

    // ASSERT
    expect(api.apiCollectionsGet).toHaveBeenCalledWith({ signal })
  })

  it('shouldFailWhenNoNameMatches', async () => {
    const api = { apiCollectionsGet: vi.fn().mockResolvedValue({ collections: [] }) }
    await expect(resolveCollectionId(api, 'nope')).rejects.toThrow(/No collection found/)
  })
})

describe('parseTagNames', () => {
  it('shouldSplitTrimAndDeduplicate', () => {
    expect(parseTagNames(' dev, api ,dev,, DEV ')).toEqual(['dev', 'api'])
  })
})

describe('resolveTagIds', () => {
  it('shouldResolveExistingTagsAndCreateUnknownOnes', async () => {
    // ARRANGE
    const api = {
      apiTagsGet: vi.fn().mockResolvedValue({ tagList: [tag('t1', 'Dev')] }),
      apiTagsPost: vi.fn().mockResolvedValue(tag('t2', 'api')),
    }

    // ACT
    const ids = await resolveTagIds(api, UUID_A, ['dev', 'api'])

    // ASSERT
    expect(ids).toEqual(['t1', 't2'])
    expect(api.apiTagsPost).toHaveBeenCalledExactlyOnceWith({
      tagSaveJson: { collectionId: UUID_A, name: 'api' },
    })
  })

  it('shouldNotFetchAnythingForEmptyInput', async () => {
    const api = { apiTagsGet: vi.fn(), apiTagsPost: vi.fn() }
    expect(await resolveTagIds(api, UUID_A, [])).toEqual([])
    expect(api.apiTagsGet).not.toHaveBeenCalled()
  })
})

describe('resolveFolderId', () => {
  const existing = [folder('f1', 'Dev'), folder('f2', 'TypeScript', 'f1')]

  it('shouldWalkNestedPathsCaseInsensitively', async () => {
    const api = {
      apiFoldersGet: vi.fn().mockResolvedValue({ folderList: existing }),
      apiFoldersPost: vi.fn(),
    }
    expect(await resolveFolderId(api, UUID_A, 'dev/typescript', { create: false })).toBe('f2')
    expect(api.apiFoldersPost).not.toHaveBeenCalled()
  })

  it('shouldCreateMissingSegmentsWhenAllowed', async () => {
    // ARRANGE
    const api = {
      apiFoldersGet: vi.fn().mockResolvedValue({ folderList: existing }),
      apiFoldersPost: vi.fn().mockResolvedValue(folder('f3', 'Articles', 'f2')),
    }

    // ACT
    const id = await resolveFolderId(api, UUID_A, 'Dev/TypeScript/Articles', { create: true })

    // ASSERT
    expect(id).toBe('f3')
    expect(api.apiFoldersPost).toHaveBeenCalledExactlyOnceWith({
      folderSaveJson: { collectionId: UUID_A, parentId: 'f2', name: 'Articles' },
    })
  })

  it('shouldFailOnMissingSegmentsWhenCreationIsDisabled', async () => {
    const api = {
      apiFoldersGet: vi.fn().mockResolvedValue({ folderList: existing }),
      apiFoldersPost: vi.fn(),
    }
    await expect(
      resolveFolderId(api, UUID_A, 'Dev/Missing', { create: false }),
    ).rejects.toThrow(/No folder found at path/)
  })

  it('shouldIgnoreSoftDeletedFolders', async () => {
    const api = {
      apiFoldersGet: vi
        .fn()
        .mockResolvedValue({ folderList: [{ ...folder('f9', 'Dev'), deletedAt: new Date() }] }),
      apiFoldersPost: vi.fn().mockResolvedValue(folder('f10', 'Dev')),
    }
    expect(await resolveFolderId(api, UUID_A, 'Dev', { create: true })).toBe('f10')
  })
})

describe('CliError', () => {
  it('shouldDefaultToExitCodeOne', () => {
    expect(new CliError('boom').exitCode).toBe(1)
  })
})

describe('folderPaths', () => {
  it('shouldPairEachFolderWithItsOwnPath', () => {
    // ARRANGE: deliberately out of tree order, and a child listed before its
    // parent — the pairing must follow the input, not the hierarchy.
    const input = [folder('f2', 'TypeScript', 'f1'), folder('f1', 'Dev'), folder('f3', 'Ops')]

    // ACT
    const paths = folderPaths(input)

    // ASSERT: ids travel with their paths, so a caller cannot line up the
    // wrong pair the way a parallel string[] invited.
    expect(paths.map((entry) => [entry.folder.id, entry.path])).toEqual([
      ['f2', 'Dev/TypeScript'],
      ['f1', 'Dev'],
      ['f3', 'Ops'],
    ])
  })

  it('shouldBuildPathsThroughSeveralLevels', () => {
    const input = [
      folder('f3', 'Deep', 'f2'),
      folder('f2', 'Java', 'f1'),
      folder('f1', 'Dev'),
    ]

    expect(folderPaths(input).map((e) => e.path)).toEqual(['Dev/Java/Deep', 'Dev/Java', 'Dev'])
  })

  it('shouldNotFilterAnything', () => {
    // ARRANGE: the trashbin passes soft-deleted folders on purpose. Dropping
    // them here would silently shorten the result and desync any caller that
    // filtered separately.
    const input = [
      folder('f1', 'Dev'),
      { ...folder('f9', 'Trashed'), deletedAt: new Date() },
      folder('f3', 'Ops'),
    ]

    // ACT
    const paths = folderPaths(input)

    // ASSERT
    expect(paths).toHaveLength(3)
    expect(paths[1]?.folder.id).toBe('f9')
  })

  it('shouldTerminateOnAParentCycleInServerData', () => {
    const paths = folderPaths([folder('a', 'A', 'b'), folder('b', 'B', 'a')])

    expect(paths).toHaveLength(2)
    expect(paths[0]?.path).toBe('B/A')
  })
})

describe('normalizeFolderPath', () => {
  it('shouldCollapseStrayWhitespaceAndSeparators', () => {
    // So a path typed by hand still matches what `folders list` prints.
    expect(normalizeFolderPath(' Dev / TypeScript/ ')).toBe('Dev/TypeScript')
  })

  it('shouldReduceARootishPathToTheEmptyString', () => {
    expect(normalizeFolderPath('/')).toBe('')
    expect(normalizeFolderPath('///')).toBe('')
  })
})

describe('folderPathSegments', () => {
  it('shouldDropEmptySegmentsRatherThanYieldBlankNames', () => {
    expect(folderPathSegments('Dev//TypeScript/')).toEqual(['Dev', 'TypeScript'])
  })
})

describe('parentFolderPath', () => {
  it('shouldDropTheLeafSegment', () => {
    expect(parentFolderPath('Dev/TypeScript/Articles')).toBe('Dev/TypeScript')
  })

  it('shouldBeEmptyForATopLevelFolder', () => {
    expect(parentFolderPath('Dev')).toBe('')
  })
})

describe('findCollection', () => {
  it('shouldMatchByNameCaseInsensitively', async () => {
    const api = {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [collection(UUID_A, 'Personal'), collection(UUID_B, 'Work')],
      }),
    }

    await expect(findCollection(api, 'work')).resolves.toMatchObject({ id: UUID_B })
  })

  it('shouldMatchByIdWithoutFallingBackToTheName', async () => {
    // ARRANGE
    const api = {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [collection(UUID_A, 'Personal')],
      }),
    }

    // ACT & ASSERT — a UUID that is not present must not match some collection
    // whose *name* happens to be that string.
    await expect(findCollection(api, UUID_B)).rejects.toThrow(CliError)
  })

  it('shouldRefuseToGuessBetweenDuplicateNames', async () => {
    const api = {
      apiCollectionsGet: vi.fn().mockResolvedValue({
        collections: [collection(UUID_A, 'Work'), collection(UUID_B, 'work')],
      }),
    }

    await expect(findCollection(api, 'Work')).rejects.toThrow(/Multiple collections match/)
  })
})

describe('findTag', () => {
  it('shouldReturnTheWholeTagSoCallersCanPreserveItsColour', async () => {
    // ARRANGE
    const coloured: TagJson = {
      id: UUID_B,
      entityInfo,
      data: { collectionId: UUID_A, name: 'dev', color: '#ff0000' },
    }
    const api = { apiTagsGet: vi.fn().mockResolvedValue({ tagList: [coloured] }) }

    // ACT
    const found = await findTag(api, UUID_A, 'DEV')

    // ASSERT
    expect(found.data.color).toBe('#ff0000')
  })

  it('shouldNeverCreateAMissingTag', async () => {
    // ARRANGE: unlike resolveTagIds, a rename or delete of a tag that is not
    // there is a mistake, not a request to make one.
    const api = { apiTagsGet: vi.fn().mockResolvedValue({ tagList: [] }) }

    // ACT & ASSERT
    await expect(findTag(api, UUID_A, 'nope')).rejects.toThrow(/No tag found matching/)
  })
})

describe('findFolder', () => {
  // Deliberately un-annotated: an explicit `{ apiFoldersGet: Mock }` would
  // erase the call signature vi.fn() infers, and stop matching FolderLookupApi.
  const folderApi = () => ({
    apiFoldersGet: vi.fn().mockResolvedValue({
      folderList: [
        folder('f1', 'Dev'),
        folder('f2', 'TypeScript', 'f1'),
        { ...folder(UUID_B, 'Trashed'), deletedAt: new Date() },
      ],
    }),
  })

  it('shouldReturnTheFolderAndItsFullPath', async () => {
    const found = await findFolder(folderApi(), UUID_A, 'dev/typescript')

    expect(found.folder.id).toBe('f2')
    expect(found.path).toBe('Dev/TypeScript')
  })

  it('shouldExposeTheParentSoARenameCanKeepIt', async () => {
    // The rename endpoint reads an absent parentId as "move to the root".
    const found = await findFolder(folderApi(), UUID_A, 'Dev/TypeScript')

    expect(found.folder.data.parentId).toBe('f1')
  })

  it('shouldIgnoreTrashedFolders', async () => {
    // They belong to the trashbin; treating one as live would resurrect it.
    await expect(findFolder(folderApi(), UUID_A, 'Trashed')).rejects.toThrow(/No folder found/)
  })

  it('shouldRejectAPathThatNamesNoFolder', async () => {
    await expect(findFolder(folderApi(), UUID_A, '/')).rejects.toThrow(/Invalid folder path/)
  })

  it('shouldReportAMissingIdAsAnIdRatherThanAPath', async () => {
    await expect(findFolder(folderApi(), UUID_A, UUID_B)).rejects.toThrow(
      `No folder found with ID '${UUID_B}' in the collection.`,
    )
  })
})
