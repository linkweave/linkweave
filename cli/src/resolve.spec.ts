import { describe, expect, it, vi } from 'vitest'

import type { CollectionSummaryJson, FolderJson, TagJson } from './api'
import { CliError } from './errors'
import {
  looksLikeId,
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
  return { id, entityInfo, data: { collectionId: UUID_A, name, parentId } }
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
