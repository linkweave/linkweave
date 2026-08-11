import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { BookmarkJson, TagJson } from '../api'
import { ResponseError } from '../api'

// The commands pull their credentials and clients from these two modules;
// stubbing them keeps the tests off the network and out of the real config dir.
const CONFIG = { server: 'https://test.example', apiKey: 'lw_test' }

// Reassigned per test — the factories below dereference it lazily, at call
// time, so there is no temporal-dead-zone problem with vi.mock hoisting.
let clients: {
  bookmarks: { apiBookmarksGet: ReturnType<typeof vi.fn>; apiBookmarksPost: ReturnType<typeof vi.fn>; apiBookmarksBookmarkIdGet: ReturnType<typeof vi.fn>; apiBookmarksBookmarkIdPut: ReturnType<typeof vi.fn> }
  tags: { apiTagsGet: ReturnType<typeof vi.fn>; apiTagsPost: ReturnType<typeof vi.fn> }
  folders: { apiFoldersGet: ReturnType<typeof vi.fn>; apiFoldersPost: ReturnType<typeof vi.fn> }
  export: { apiCollectionsCollectionIdExportGetRaw: ReturnType<typeof vi.fn> }
  import: { apiCollectionsCollectionIdImportPost: ReturnType<typeof vi.fn> }
}

vi.mock('../config', () => ({
  resolveEffectiveConfig: () => CONFIG,
  loadStoredConfig: () => undefined,
}))
vi.mock('../client', () => ({ createAuthenticatedClients: () => clients }))

const {
  runBookmarksAdd,
  runBookmarksEdit,
  runBookmarksExport,
  runBookmarksImport,
  runBookmarksList,
  runBookmarksShow,
} = await import('./bookmarksCmd')

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const EXPORT_HTML = '<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><DT><A HREF="https://x.example">X</A></DL>\n'
// Populated rather than `{}`: the generated serialiser calls toISOString() on
// both timestamps, so an empty stand-in fails on any JSON output path.
const entityInfo: TagJson['entityInfo'] = {
  timestampErstellt: new Date('2026-01-15T09:30:00Z'),
  timestampMutiert: new Date('2026-02-01T14:05:00Z'),
  userErstellt: 'dev@example.com',
  userMutiert: 'dev@example.com',
}

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
    export: {
      // The generated call is the *Raw variant, because the typed one models
      // this text/html endpoint as returning nothing.
      apiCollectionsCollectionIdExportGetRaw: vi.fn().mockResolvedValue({
        raw: { text: () => Promise.resolve(EXPORT_HTML) },
      }),
    },
    import: {
      apiCollectionsCollectionIdImportPost: vi
        .fn()
        .mockResolvedValue({ foldersCreated: 2, bookmarksCreated: 7, bookmarksSkipped: 0 }),
    },
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

describe('runBookmarksShow', () => {
  let stdout: string

  beforeEach(() => {
    stdout = ''
    vi.mocked(console.log).mockImplementation((...args: unknown[]) => {
      stdout += args.join(' ') + '\n'
    })
    clients.folders.apiFoldersGet.mockResolvedValue({ folderList: [] })
  })

  it('shouldResolveTagIdsToNamesForTheTable', async () => {
    // ARRANGE: an ID column would make the record unreadable at a glance.

    // ACT
    await runBookmarksShow('b1', { format: 'table' }, cmd)

    // ASSERT
    expect(stdout).toMatch(/Tags\s+dev/)
  })

  it('shouldLabelEveryFieldDownThePage', async () => {
    // ACT
    await runBookmarksShow('b1', { format: 'table' }, cmd)

    // ASSERT — one record reads better as rows than as columns.
    for (const field of ['ID', 'Title', 'URL', 'Collection', 'Folder', 'Tags', 'Clicks']) {
      expect(stdout, `${field} should be labelled`).toContain(field)
    }
  })

  it('shouldShowTimestampsToTheMinuteRatherThanAsIsoStrings', async () => {
    // ARRANGE: seconds and a trailing Z are noise when reading one record.

    // ACT
    await runBookmarksShow('b1', { format: 'table' }, cmd)

    // ASSERT
    expect(stdout).toMatch(/Created\s+2026-01-15 09:30$/m)
    expect(stdout).toMatch(/Updated\s+2026-02-01 14:05$/m)
  })

  it('shouldNotPayForNameLookupsTheOutputWillNotShow', async () => {
    // ARRANGE: json is the raw payload and ids is one line; neither needs the
    // tag or folder round trips the table does.

    // ACT
    await runBookmarksShow('b1', { format: 'json' }, cmd)

    // ASSERT
    expect(clients.tags.apiTagsGet).not.toHaveBeenCalled()
    expect(clients.folders.apiFoldersGet).not.toHaveBeenCalled()
  })

  it('shouldPrintJustTheIdForIdsFormat', async () => {
    await runBookmarksShow('b1', { format: 'ids' }, cmd)

    expect(stdout.trim()).toBe('b1')
  })

  it('shouldEmitTheWholeBookmarkAsJson', async () => {
    await runBookmarksShow('b1', { format: 'json' }, cmd)

    const parsed = JSON.parse(stdout)
    expect(parsed.id).toBe('b1')
    expect(parsed.data.url).toBe('https://quarkus.io/guides')
  })

  it('shouldSkipTheFolderLookupForABookmarkWithNoFolder', async () => {
    // ARRANGE: the fixture bookmark sits at the collection root.

    // ACT
    await runBookmarksShow('b1', { format: 'table' }, cmd)

    // ASSERT
    expect(clients.folders.apiFoldersGet).not.toHaveBeenCalled()
  })

  it('shouldReportAMissingBookmarkByItsId', async () => {
    // ARRANGE
    clients.bookmarks.apiBookmarksBookmarkIdGet.mockRejectedValue(
      new ResponseError(new Response('', { status: 404 }), 'not found'),
    )

    // ACT & ASSERT
    await expect(runBookmarksShow('nope', { format: 'table' }, cmd)).rejects.toThrow(
      'Bookmark not found: nope',
    )
  })
})

describe('runBookmarksExport', () => {
  let dir: string
  let stdout: string

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'linkweave-export-'))
    stdout = ''
    vi.spyOn(process.stdout, 'write').mockImplementation((chunk: string | Uint8Array) => {
      stdout += String(chunk)
      return true
    })
  })

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true })
  })

  it('shouldWriteTheHtmlToStdoutSoItCanBeRedirected', async () => {
    // ARRANGE: no --output means the export is data on stdout (BR-018).

    // ACT
    await runBookmarksExport({ collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(stdout).toBe(EXPORT_HTML)
  })

  it('shouldWriteToAFileWhenOutputIsGiven', async () => {
    // ARRANGE
    const target = join(dir, 'bookmarks.html')

    // ACT
    await runBookmarksExport({ collection: COLLECTION_ID, output: target }, cmd)

    // ASSERT — and nothing on stdout, which would corrupt a redirect.
    expect(readFileSync(target, 'utf-8')).toBe(EXPORT_HTML)
    expect(stdout).toBe('')
  })

  it('shouldReadTheBodyOffTheRawResponse', async () => {
    // ARRANGE: the typed generated call returns void for this endpoint, so
    // using it would silently export an empty file.
    await runBookmarksExport({ collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(clients.export.apiCollectionsCollectionIdExportGetRaw).toHaveBeenCalledWith({
      collectionId: COLLECTION_ID,
    })
  })

  it('shouldReportAnUnwritableOutputPath', async () => {
    await expect(
      runBookmarksExport({ collection: COLLECTION_ID, output: join(dir, 'no', 'such', 'x.html') }, cmd),
    ).rejects.toThrow(/Cannot write to/)
  })
})

describe('runBookmarksImport', () => {
  let dir: string

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'linkweave-import-'))
    vi.spyOn(console, 'log').mockImplementation(() => {})
  })

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true })
  })

  /** Writes a file into the temp dir and returns its path. */
  function file(name: string, content = EXPORT_HTML): string {
    const path = join(dir, name)
    writeFileSync(path, content)
    return path
  }

  /** The File the command uploaded. */
  function uploaded(): File {
    return clients.import.apiCollectionsCollectionIdImportPost.mock.calls[0]![0].file
  }

  it('shouldUploadTheFileContents', async () => {
    // ACT
    await runBookmarksImport(file('bookmarks.html'), { collection: COLLECTION_ID }, cmd)

    // ASSERT
    await expect(uploaded().text()).resolves.toBe(EXPORT_HTML)
  })

  it('shouldSendAFileNameSoTheServerAcceptsTheUpload', async () => {
    // ARRANGE: the generated client appends the blob without a filename
    // argument, so the name has to ride on the object itself.

    // ACT
    await runBookmarksImport(file('my-links.html'), { collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(uploaded().name).toBe('my-links.html')
  })

  it('shouldLowercaseOnlyTheExtensionOfAnUppercaseName', async () => {
    // ARRANGE: the server matches '.html' case-sensitively but records the
    // name as the bookmarks' import source, so the stem must survive.

    // ACT
    await runBookmarksImport(file('Chrome Export.HTML'), { collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(uploaded().name).toBe('Chrome Export.html')
  })

  it('shouldRejectAFileThatIsNotHtmlBeforeUploading', async () => {
    // ACT & ASSERT
    await expect(
      runBookmarksImport(file('links.json', '[]'), { collection: COLLECTION_ID }, cmd),
    ).rejects.toThrow(/is not a bookmarks HTML file/)
    expect(clients.import.apiCollectionsCollectionIdImportPost).not.toHaveBeenCalled()
  })

  it('shouldRejectAnEmptyFile', async () => {
    await expect(
      runBookmarksImport(file('empty.html', ''), { collection: COLLECTION_ID }, cmd),
    ).rejects.toThrow(/is empty/)
  })

  it('shouldRejectAFileOverTheServersFiveMegabyteCapBeforeUploading', async () => {
    // ARRANGE: failing here beats spending the upload to be told no.
    const big = file('big.html', 'x'.repeat(5 * 1024 * 1024 + 1))

    // ACT & ASSERT
    await expect(runBookmarksImport(big, { collection: COLLECTION_ID }, cmd)).rejects.toThrow(
      /the server accepts at most 5 MB/,
    )
    expect(clients.import.apiCollectionsCollectionIdImportPost).not.toHaveBeenCalled()
  })

  it('shouldReportAMissingFile', async () => {
    await expect(
      runBookmarksImport(join(dir, 'nope.html'), { collection: COLLECTION_ID }, cmd),
    ).rejects.toThrow(/Cannot read/)
  })

  it('shouldSummariseWhatWasCreated', async () => {
    // ARRANGE
    const logged: string[] = []
    vi.mocked(console.log).mockImplementation((...args: unknown[]) => {
      logged.push(args.join(' '))
    })

    // ACT
    await runBookmarksImport(file('bookmarks.html'), { collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(logged.join('\n')).toContain('✓ Imported 7 bookmark(s) into 2 new folder(s).')
  })

  it('shouldMentionSkippedDuplicatesOnlyWhenThereAreAny', async () => {
    // ARRANGE
    const logged: string[] = []
    vi.mocked(console.log).mockImplementation((...args: unknown[]) => {
      logged.push(args.join(' '))
    })
    clients.import.apiCollectionsCollectionIdImportPost.mockResolvedValue({
      foldersCreated: 0,
      bookmarksCreated: 1,
      bookmarksSkipped: 3,
    })

    // ACT
    await runBookmarksImport(file('bookmarks.html'), { collection: COLLECTION_ID }, cmd)

    // ASSERT
    expect(logged.join('\n')).toContain('3 skipped as duplicates')
  })
})
