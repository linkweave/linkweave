import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { BookmarkJson, FolderJson, TagJson } from '../api'
import type { CliError } from '../errors'

const CONFIG = { server: 'https://test.example', apiKey: 'lw_test' }

let clients: Record<string, Record<string, ReturnType<typeof vi.fn>>>
let answers: string[]
let questions: string[]

vi.mock('../config', () => ({
  resolveEffectiveConfig: () => CONFIG,
  loadStoredConfig: () => undefined,
}))
vi.mock('../client', () => ({ createAuthenticatedClients: () => clients }))
vi.mock('node:readline/promises', () => ({
  createInterface: () => ({
    question: async (text: string) => {
      questions.push(text)
      return answers.shift() ?? ''
    },
    close: () => {},
  }),
}))

const { runTrashEmpty, runTrashList, runTrashPurge, runTrashRestore } = await import('./trashCmd')

const COLLECTION_ID = '550e8400-e29b-41d4-a716-446655440000'
const entityInfo = {} as TagJson['entityInfo']
const cmd = { optsWithGlobals: () => ({}) } as unknown as Command

function bookmark(id: string, title: string, deletedAt: Date): BookmarkJson {
  return {
    id,
    entityInfo,
    sortOrder: 0,
    clickCount: 0,
    propertyValues: [],
    deletedAt,
    data: { collectionId: COLLECTION_ID, title, url: `https://example.com/${id}` },
  }
}

function folder(id: string, name: string, deletedAt: Date, parentId?: string): FolderJson {
  return { id, entityInfo, sortOrder: 0, deletedAt, data: { collectionId: COLLECTION_ID, name, parentId } }
}

let stdout: string
let stderr: string

function setTty(isTty: boolean): void {
  Object.defineProperty(process.stdin, 'isTTY', { value: isTty, configurable: true })
}

beforeEach(() => {
  stdout = ''
  stderr = ''
  answers = []
  questions = []
  setTty(true)
  vi.spyOn(console, 'log').mockImplementation((...args: unknown[]) => {
    stdout += args.join(' ') + '\n'
  })
  vi.spyOn(process.stderr, 'write').mockImplementation(((chunk: unknown) => {
    stderr += String(chunk)
    return true
  }) as never)
  clients = {
    trash: {
      apiTrashbinGet: vi.fn().mockResolvedValue({
        bookmarks: [bookmark('b1', 'Quarkus Guides', new Date('2026-08-01T10:00:00Z'))],
        folders: [folder('f1', 'Archive', new Date('2026-08-02T10:00:00Z'))],
      }),
      apiTrashbinCountGet: vi.fn().mockResolvedValue({ count: 2 }),
      apiTrashbinBookmarksBookmarkIdRestorePost: vi.fn().mockResolvedValue({}),
      apiTrashbinFoldersFolderIdRestorePost: vi.fn().mockResolvedValue({}),
      apiTrashbinBookmarksBookmarkIdDelete: vi.fn().mockResolvedValue(undefined),
      apiTrashbinFoldersFolderIdDelete: vi.fn().mockResolvedValue(undefined),
      apiTrashbinDelete: vi.fn().mockResolvedValue(undefined),
    },
  }
})

afterEach(() => {
  vi.restoreAllMocks()
  Object.defineProperty(process.stdin, 'isTTY', { value: undefined, configurable: true })
})

/** Runs something expected to fail and returns the CliError it threw. */
async function failureOf(run: Promise<void>): Promise<CliError> {
  try {
    await run
  } catch (e) {
    return e as CliError
  }
  throw new Error('expected the command to fail, but it succeeded')
}

describe('runTrashList', () => {
  it('shouldShowBookmarksAndFoldersInOneTableNewestFirst', async () => {
    await runTrashList({ format: 'table' }, cmd)

    // The folder was deleted later, so it leads.
    expect(stdout).toMatch(/folder\s+f1\s+Archive/)
    expect(stdout).toMatch(/bookmark\s+b1\s+Quarkus Guides/)
    expect(stdout.indexOf('Archive')).toBeLessThan(stdout.indexOf('Quarkus Guides'))
  })

  it('shouldSaySoWhenTheTrashIsEmpty', async () => {
    clients['trash']!['apiTrashbinGet']!.mockResolvedValue({ bookmarks: [], folders: [] })

    await runTrashList({ format: 'table' }, cmd)

    expect(stdout.trim()).toBe('The trash is empty.')
  })

  it('shouldEmitIdsForScripting', async () => {
    await runTrashList({ format: 'ids' }, cmd)

    expect(stdout.split('\n').filter(Boolean)).toEqual(['f1', 'b1'])
  })
})

describe('runTrashRestore', () => {
  it('shouldPickTheBookmarkEndpointForABookmarkId', async () => {
    await runTrashRestore('b1', {}, cmd)

    expect(clients['trash']!['apiTrashbinBookmarksBookmarkIdRestorePost']).toHaveBeenCalledWith({
      bookmarkId: 'b1',
    })
    expect(clients['trash']!['apiTrashbinFoldersFolderIdRestorePost']).not.toHaveBeenCalled()
    expect(stdout).toContain('✓ Restored bookmark: Quarkus Guides')
  })

  it('shouldPickTheFolderEndpointForAFolderId', async () => {
    // The user should not have to tell the CLI which kind of thing an ID is.
    await runTrashRestore('f1', {}, cmd)

    expect(clients['trash']!['apiTrashbinFoldersFolderIdRestorePost']).toHaveBeenCalledWith({
      folderId: 'f1',
    })
    expect(stdout).toContain('✓ Restored folder: Archive')
  })

  it('shouldFailClearlyForAnIdThatIsNotInTheTrash', async () => {
    const failure = await failureOf(runTrashRestore('nope', {}, cmd))

    expect(failure.message).toMatch(/Nothing with ID 'nope' is in the trash/)
  })
})

describe('runTrashPurge', () => {
  it('shouldDeletePermanentlyAfterConfirmation', async () => {
    // ARRANGE
    answers = ['y']

    // ACT
    await runTrashPurge('b1', {}, cmd)

    // ASSERT
    expect(clients['trash']!['apiTrashbinBookmarksBookmarkIdDelete']).toHaveBeenCalledWith({
      bookmarkId: 'b1',
    })
  })

  it('shouldAbortWhenTheUserDeclines', async () => {
    // ARRANGE: anything other than y/yes means no.
    answers = ['']

    // ACT
    const failure = await failureOf(runTrashPurge('b1', {}, cmd))

    // ASSERT
    expect(failure.message).toBe('Aborted.')
    expect(clients['trash']!['apiTrashbinBookmarksBookmarkIdDelete']).not.toHaveBeenCalled()
  })

  it('shouldSkipThepromptWithYes', async () => {
    await runTrashPurge('f1', { yes: true }, cmd)

    expect(clients['trash']!['apiTrashbinFoldersFolderIdDelete']).toHaveBeenCalledWith({
      folderId: 'f1',
    })
  })

  it('shouldRefuseToPurgeUnattendedWithoutYes', async () => {
    // ARRANGE: a pipe has nobody to answer the prompt. Assuming consent for an
    // irreversible delete is the wrong default.
    setTty(false)

    // ACT
    const failure = await failureOf(runTrashPurge('b1', {}, cmd))

    // ASSERT
    expect(failure.message).toMatch(/Refusing in non-interactive mode/)
    expect(failure.exitCode).toBe(2)
    expect(clients['trash']!['apiTrashbinBookmarksBookmarkIdDelete']).not.toHaveBeenCalled()
  })
})

describe('runTrashEmpty', () => {
  it('shouldReportTheCountItIsAboutToDestroy', async () => {
    answers = ['yes']

    await runTrashEmpty({}, cmd)

    expect(questions[0]).toContain('Permanently delete all 2 item(s)')
    expect(clients['trash']!['apiTrashbinDelete']).toHaveBeenCalled()
  })

  it('shouldDoNothingWhenAlreadyEmpty', async () => {
    clients['trash']!['apiTrashbinCountGet']!.mockResolvedValue({ count: 0 })

    await runTrashEmpty({}, cmd)

    expect(stdout.trim()).toBe('The trash is already empty.')
    expect(clients['trash']!['apiTrashbinDelete']).not.toHaveBeenCalled()
  })
})
