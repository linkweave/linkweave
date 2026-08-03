import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { BookmarkJson, TagJson } from '../api'

// The commands pull their credentials and clients from these two modules;
// stubbing them keeps the tests off the network and out of the real config dir.
const CONFIG = { server: 'https://test.example', apiKey: 'lw_test' }

// Reassigned per test — the factories below dereference it lazily, at call
// time, so there is no temporal-dead-zone problem with vi.mock hoisting.
let clients: {
  bookmarks: { apiBookmarksGet: ReturnType<typeof vi.fn>; apiBookmarksPost: ReturnType<typeof vi.fn>; apiBookmarksBookmarkIdGet: ReturnType<typeof vi.fn>; apiBookmarksBookmarkIdPut: ReturnType<typeof vi.fn> }
  tags: { apiTagsGet: ReturnType<typeof vi.fn>; apiTagsPost: ReturnType<typeof vi.fn> }
  folders: { apiFoldersGet: ReturnType<typeof vi.fn>; apiFoldersPost: ReturnType<typeof vi.fn> }
}

vi.mock('../config', () => ({ resolveEffectiveConfig: () => CONFIG }))
vi.mock('../client', () => ({ createAuthenticatedClients: () => clients }))

const { runBookmarksAdd, runBookmarksEdit, runBookmarksList } = await import('./bookmarksCmd')

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const entityInfo = {} as TagJson['entityInfo']

/** `cmd` is only used to read the global --server/--api-key options. */
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function tag(id: string, name: string): TagJson {
  return { id, entityInfo, data: { collectionId: COLLECTION_ID, name } }
}

function bookmark(id: string, tagIds: string[]): BookmarkJson {
  return {
    id,
    entityInfo,
    sortOrder: 0,
    clickCount: 0,
    propertyValues: [],
    data: {
      collectionId: COLLECTION_ID,
      title: 'Quarkus Guides',
      url: 'https://quarkus.io/guides',
      tagIds: new Set(tagIds),
    },
  }
}

beforeEach(() => {
  clients = {
    bookmarks: {
      apiBookmarksGet: vi.fn().mockResolvedValue({ bookmarkList: [bookmark('b1', ['t1'])] }),
      apiBookmarksPost: vi.fn().mockResolvedValue(bookmark('b1', [])),
      apiBookmarksBookmarkIdGet: vi.fn().mockResolvedValue(bookmark('b1', ['t1'])),
      apiBookmarksBookmarkIdPut: vi.fn().mockResolvedValue(bookmark('b1', [])),
    },
    tags: {
      apiTagsGet: vi.fn().mockResolvedValue({ tagList: [tag('t1', 'dev')] }),
      apiTagsPost: vi.fn(),
    },
    folders: { apiFoldersGet: vi.fn(), apiFoldersPost: vi.fn() },
  }
  vi.spyOn(console, 'log').mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

/** The tagIds sent by the last PUT/POST call. */
function sentTagIds(mock: ReturnType<typeof vi.fn>): Set<string> | undefined {
  return mock.mock.calls[0]![0].bookmarkSaveJson.tagIds
}

describe('runBookmarksEdit', () => {
  it('shouldKeepTheExistingTagsWhenTagsFlagIsOmitted', async () => {
    await runBookmarksEdit('b1', { title: 'Renamed' }, cmd)

    expect(sentTagIds(clients.bookmarks.apiBookmarksBookmarkIdPut)).toEqual(new Set(['t1']))
    expect(clients.tags.apiTagsGet).not.toHaveBeenCalled()
  })

  it('shouldClearAllTagsOnAnEmptyTagsFlag', async () => {
    // ARRANGE: `--tags ''` is a request for "no tags", not a missing flag.

    // ACT
    await runBookmarksEdit('b1', { tags: '' }, cmd)

    // ASSERT
    expect(sentTagIds(clients.bookmarks.apiBookmarksBookmarkIdPut)).toEqual(new Set())
  })

  it('shouldReplaceTheTagSetWhenTagsAreGiven', async () => {
    await runBookmarksEdit('b1', { tags: 'dev' }, cmd)

    expect(sentTagIds(clients.bookmarks.apiBookmarksBookmarkIdPut)).toEqual(new Set(['t1']))
  })

  it('shouldRejectAnEditWithoutAnyField', async () => {
    await expect(runBookmarksEdit('b1', {}, cmd)).rejects.toThrow(/Nothing to update/)
  })
})

describe('runBookmarksAdd', () => {
  it('shouldSendAnEmptyTagSetOnAnEmptyTagsFlag', async () => {
    await runBookmarksAdd('https://example.com', { collection: COLLECTION_ID, tags: '' }, cmd)

    expect(sentTagIds(clients.bookmarks.apiBookmarksPost)).toEqual(new Set())
  })

  it('shouldOmitTagsEntirelyWhenTheFlagIsAbsent', async () => {
    await runBookmarksAdd('https://example.com', { collection: COLLECTION_ID }, cmd)

    expect(sentTagIds(clients.bookmarks.apiBookmarksPost)).toBeUndefined()
  })
})

describe('runBookmarksList', () => {
  it('shouldFetchTagsOnlyOnceWhenFilteringByTagInTableFormat', async () => {
    // ARRANGE: the table needs tag names and --tag needs a tag ID; both read
    // the same list.

    // ACT
    await runBookmarksList({ collection: COLLECTION_ID, tag: 'dev', format: 'table' }, cmd)

    // ASSERT
    expect(clients.tags.apiTagsGet).toHaveBeenCalledOnce()
  })

  it('shouldNotFetchTagsForIdsOutputWithoutATagFilter', async () => {
    await runBookmarksList({ collection: COLLECTION_ID, format: 'ids' }, cmd)

    expect(clients.tags.apiTagsGet).not.toHaveBeenCalled()
  })

  it('shouldFailOnAnUnknownTagName', async () => {
    await expect(
      runBookmarksList({ collection: COLLECTION_ID, tag: 'nope', format: 'ids' }, cmd),
    ).rejects.toThrow(/No tag found with name 'nope'/)
  })
})
