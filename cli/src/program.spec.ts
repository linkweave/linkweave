import { CommanderError } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Every action is stubbed, so these tests are purely about argv parsing:
// which handler a command line reaches and what it is handed. The constants
// the program reads at build time (option choices, completion sources) come
// from the real modules, hence importOriginal rather than a bare factory.
vi.mock('./commands/bookmarksCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runBookmarksAdd: vi.fn(),
  runBookmarksEdit: vi.fn(),
  runBookmarksList: vi.fn(),
  runBookmarksRm: vi.fn(),
  runBookmarksShow: vi.fn(),
  runBookmarksExport: vi.fn(),
  runBookmarksImport: vi.fn(),
}))
vi.mock('./commands/collectionsCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runCollectionsList: vi.fn(),
  runCollectionsCreate: vi.fn(),
  runCollectionsRename: vi.fn(),
  runCollectionsRm: vi.fn(),
  runCollectionsSetDefault: vi.fn(),
}))
vi.mock('./commands/tagsCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runTagsList: vi.fn(),
  runTagsRename: vi.fn(),
  runTagsRm: vi.fn(),
}))
vi.mock('./commands/foldersCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runFoldersList: vi.fn(),
  runFoldersCreate: vi.fn(),
  runFoldersRename: vi.fn(),
  runFoldersMv: vi.fn(),
  runFoldersRm: vi.fn(),
}))
vi.mock('./commands/trashCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runTrashList: vi.fn(),
  runTrashRestore: vi.fn(),
  runTrashPurge: vi.fn(),
  runTrashEmpty: vi.fn(),
}))
vi.mock('./commands/loginCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runLogin: vi.fn(),
}))
vi.mock('./commands/logoutCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runLogout: vi.fn(),
}))
// runComplete calls process.exit() on every path; stubbing it keeps the
// hidden __complete command testable without taking the runner down with it.
vi.mock('./commands/completeCmd', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  runComplete: vi.fn(),
}))

const { buildProgram } = await import('./program')
const bookmarksCmd = await import('./commands/bookmarksCmd')
const collectionsCmd = await import('./commands/collectionsCmd')
const tagsCmd = await import('./commands/tagsCmd')
const foldersCmd = await import('./commands/foldersCmd')
const trashCmd = await import('./commands/trashCmd')
const loginCmd = await import('./commands/loginCmd')
const logoutCmd = await import('./commands/logoutCmd')
const completeCmd = await import('./commands/completeCmd')

/** Parses a user-facing command line, as a shell would split it. */
async function parse(...argv: string[]): Promise<void> {
  await buildProgram().parseAsync(argv, { from: 'user' })
}

/**
 * The arguments the stubbed action was called with. The parameter is `unknown`
 * because vi.mock swaps the implementation at runtime while the static types
 * still describe the original handler signature.
 */
function argsOf(action: unknown): unknown[] {
  const { calls } = (action as { mock: { calls: unknown[][] } }).mock
  expect(calls).toHaveLength(1)
  return calls[0]!
}

/** The options object commander built, i.e. the second-to-last action arg. */
function optionsOf(action: unknown): Record<string, unknown> {
  const args = argsOf(action)
  return args[args.length - 2] as Record<string, unknown>
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('bookmarks', () => {
  it('shouldDispatchAddWithTheUrlAndEveryOption', async () => {
    // ACT
    await parse(
      'bookmarks',
      'add',
      'https://quarkus.io/guides',
      '--title',
      'Quarkus Guides',
      '--collection',
      'Work',
      '--folder',
      'Dev/Java',
      '--tags',
      'dev,java',
      '--description',
      'The guides',
    )

    // ASSERT
    expect(argsOf(bookmarksCmd.runBookmarksAdd)[0]).toBe('https://quarkus.io/guides')
    expect(optionsOf(bookmarksCmd.runBookmarksAdd)).toEqual({
      title: 'Quarkus Guides',
      collection: 'Work',
      folder: 'Dev/Java',
      tags: 'dev,java',
      description: 'The guides',
    })
  })

  it('shouldKeepAnEmptyTagsFlagDistinctFromAnOmittedOne', async () => {
    // ARRANGE: the add/edit handlers branch on `undefined` vs `''`, so parsing
    // has to preserve the difference rather than collapse both to falsy.
    await parse('bookmarks', 'add', 'https://example.com', '--tags', '')

    // ASSERT
    expect(optionsOf(bookmarksCmd.runBookmarksAdd)['tags']).toBe('')
  })

  it('shouldDefaultTheListFormatToTable', async () => {
    await parse('bookmarks', 'list')

    expect(optionsOf(bookmarksCmd.runBookmarksList)['format']).toBe('table')
  })

  it('shouldDispatchListWithTheFolderAndTagFilters', async () => {
    await parse('bookmarks', 'list', '--folder', 'Dev/Java', '--tag', 'dev', '--format', 'ids')

    expect(optionsOf(bookmarksCmd.runBookmarksList)).toEqual({
      folder: 'Dev/Java',
      tag: 'dev',
      format: 'ids',
    })
  })

  it('shouldRejectAFormatOutsideTheAllowedSet', async () => {
    await expect(parse('bookmarks', 'list', '--format', 'yaml')).rejects.toThrow(CommanderError)
    expect(bookmarksCmd.runBookmarksList).not.toHaveBeenCalled()
  })

  it('shouldDispatchEditWithTheIdAndTheEditableFields', async () => {
    // ACT
    await parse(
      'bookmarks',
      'edit',
      'b1',
      '--title',
      'Renamed',
      '--url',
      'https://example.com/moved',
      '--description',
      'New words',
      '--tags',
      'reading',
    )

    // ASSERT
    expect(argsOf(bookmarksCmd.runBookmarksEdit)[0]).toBe('b1')
    expect(optionsOf(bookmarksCmd.runBookmarksEdit)).toEqual({
      title: 'Renamed',
      url: 'https://example.com/moved',
      description: 'New words',
      tags: 'reading',
    })
  })

  it('shouldDispatchRmWithTheIdAndTheCommandOnly', async () => {
    // ARRANGE: rm's action drops the options object, so its handler takes
    // (id, cmd) rather than the usual (id, options, cmd).
    await parse('bookmarks', 'rm', 'b1')

    // ASSERT
    const args = argsOf(bookmarksCmd.runBookmarksRm)
    expect(args).toHaveLength(2)
    expect(args[0]).toBe('b1')
  })

  it('shouldRejectAddWithoutAUrl', async () => {
    await expect(parse('bookmarks', 'add')).rejects.toThrow(CommanderError)
  })
})

describe('management commands', () => {
  it('shouldDispatchBookmarksShowWithTheIdAndFormat', async () => {
    await parse('bookmarks', 'show', 'b1', '--format', 'json')

    expect(argsOf(bookmarksCmd.runBookmarksShow)[0]).toBe('b1')
    expect(optionsOf(bookmarksCmd.runBookmarksShow)['format']).toBe('json')
  })

  it('shouldDispatchBookmarksExportWithItsCollectionAndOutput', async () => {
    await parse('bookmarks', 'export', '--collection', 'Work', '--output', 'out.html')

    expect(optionsOf(bookmarksCmd.runBookmarksExport)).toEqual({
      collection: 'Work',
      output: 'out.html',
    })
  })

  it('shouldAcceptTheShortOutputFlag', async () => {
    await parse('bookmarks', 'export', '-o', 'out.html')

    expect(optionsOf(bookmarksCmd.runBookmarksExport)['output']).toBe('out.html')
  })

  it('shouldLeaveExportOutputUnsetSoItDefaultsToStdout', async () => {
    await parse('bookmarks', 'export')

    expect(optionsOf(bookmarksCmd.runBookmarksExport)['output']).toBeUndefined()
  })

  it('shouldDispatchBookmarksImportWithTheFilePath', async () => {
    await parse('bookmarks', 'import', './bookmarks.html', '--collection', 'Work')

    expect(argsOf(bookmarksCmd.runBookmarksImport)[0]).toBe('./bookmarks.html')
    expect(optionsOf(bookmarksCmd.runBookmarksImport)['collection']).toBe('Work')
  })

  it('shouldRejectImportWithoutAFile', async () => {
    await expect(parse('bookmarks', 'import')).rejects.toThrow(CommanderError)
    expect(bookmarksCmd.runBookmarksImport).not.toHaveBeenCalled()
  })

  it('shouldDispatchCollectionsCreateWithTheName', async () => {
    await parse('collections', 'create', 'Archive')

    expect(argsOf(collectionsCmd.runCollectionsCreate)[0]).toBe('Archive')
  })

  it('shouldDispatchCollectionsRenameWithBothNames', async () => {
    // ACT
    await parse('collections', 'rename', 'Work', 'Werk')

    // ASSERT — two positionals, in order.
    const args = argsOf(collectionsCmd.runCollectionsRename)
    expect([args[0], args[1]]).toEqual(['Work', 'Werk'])
  })

  it('shouldDispatchCollectionsDefault', async () => {
    await parse('collections', 'default', 'Work')

    expect(argsOf(collectionsCmd.runCollectionsSetDefault)[0]).toBe('Work')
  })

  it('shouldDispatchCollectionsRmWithTheYesFlag', async () => {
    await parse('collections', 'rm', 'Work', '--yes')

    expect(argsOf(collectionsCmd.runCollectionsRm)[0]).toBe('Work')
    expect(optionsOf(collectionsCmd.runCollectionsRm)['yes']).toBe(true)
  })

  it('shouldDispatchTagsRenameScopedToACollection', async () => {
    // ACT
    await parse('tags', 'rename', 'dev', 'devops', '--collection', 'Work')

    // ASSERT
    const args = argsOf(tagsCmd.runTagsRename)
    expect([args[0], args[1]]).toEqual(['dev', 'devops'])
    expect(optionsOf(tagsCmd.runTagsRename)['collection']).toBe('Work')
  })

  it('shouldDispatchTagsRm', async () => {
    await parse('tags', 'rm', 'dev', '-y')

    expect(argsOf(tagsCmd.runTagsRm)[0]).toBe('dev')
    expect(optionsOf(tagsCmd.runTagsRm)['yes']).toBe(true)
  })

  it('shouldDispatchFoldersCreateWithThePath', async () => {
    await parse('folders', 'create', 'Dev/Rust')

    expect(argsOf(foldersCmd.runFoldersCreate)[0]).toBe('Dev/Rust')
  })

  it('shouldDispatchFoldersRenameWithPathAndNewName', async () => {
    // ACT
    await parse('folders', 'rename', 'Dev/TypeScript', 'TS')

    // ASSERT
    const args = argsOf(foldersCmd.runFoldersRename)
    expect([args[0], args[1]]).toEqual(['Dev/TypeScript', 'TS'])
  })

  it('shouldDispatchFoldersMvWithSourceAndDestination', async () => {
    // ACT
    await parse('folders', 'mv', 'Dev/TypeScript', 'Ops')

    // ASSERT
    const args = argsOf(foldersCmd.runFoldersMv)
    expect([args[0], args[1]]).toEqual(['Dev/TypeScript', 'Ops'])
  })

  it('shouldPassASlashDestinationThroughUntouched', async () => {
    // ARRANGE: '/' means the top level, and must survive argv parsing intact.
    await parse('folders', 'mv', 'Dev/TypeScript', '/')

    // ASSERT
    expect(argsOf(foldersCmd.runFoldersMv)[1]).toBe('/')
  })

  it('shouldDispatchFoldersRm', async () => {
    await parse('folders', 'rm', 'Dev/TypeScript')

    expect(argsOf(foldersCmd.runFoldersRm)[0]).toBe('Dev/TypeScript')
  })

  it('shouldRejectAManagementCommandMissingItsSecondArgument', async () => {
    await expect(parse('folders', 'mv', 'Dev')).rejects.toThrow(CommanderError)
    expect(foldersCmd.runFoldersMv).not.toHaveBeenCalled()
  })
})

describe('listing commands', () => {
  it('shouldDispatchCollectionsList', async () => {
    await parse('collections', 'list', '--format', 'json')

    expect(optionsOf(collectionsCmd.runCollectionsList)['format']).toBe('json')
  })

  it('shouldDispatchTagsListWithTheCollectionScope', async () => {
    await parse('tags', 'list', '--collection', 'Work')

    expect(optionsOf(tagsCmd.runTagsList)).toEqual({ collection: 'Work', format: 'table' })
  })

  it('shouldDispatchFoldersListWithTheCollectionScope', async () => {
    await parse('folders', 'list', '--collection', 'Work', '--format', 'ids')

    expect(optionsOf(foldersCmd.runFoldersList)).toEqual({ collection: 'Work', format: 'ids' })
  })
})

describe('trash', () => {
  it('shouldDispatchList', async () => {
    await parse('trash', 'list', '--format', 'json')

    expect(optionsOf(trashCmd.runTrashList)['format']).toBe('json')
  })

  it('shouldDispatchRestoreWithTheId', async () => {
    await parse('trash', 'restore', 'f1')

    expect(argsOf(trashCmd.runTrashRestore)[0]).toBe('f1')
  })

  it('shouldDispatchPurgeWithTheIdAndTheYesFlag', async () => {
    await parse('trash', 'purge', 'b1', '--yes')

    expect(argsOf(trashCmd.runTrashPurge)[0]).toBe('b1')
    expect(optionsOf(trashCmd.runTrashPurge)['yes']).toBe(true)
  })

  it('shouldAcceptTheShortYesFlagOnEmpty', async () => {
    await parse('trash', 'empty', '-y')

    expect(optionsOf(trashCmd.runTrashEmpty)['yes']).toBe(true)
  })

  it('shouldLeaveYesUnsetWhenTheFlagIsAbsent', async () => {
    // The confirmation prompt is the default; only an explicit flag skips it.
    await parse('trash', 'empty')

    expect(optionsOf(trashCmd.runTrashEmpty)['yes']).toBeUndefined()
  })
})

describe('auth commands', () => {
  it('shouldPassTheGlobalOptionsToLogin', async () => {
    // ARRANGE: login reads server/api-key/insecure off the *root* command, so
    // they are written before the subcommand name.
    await parse(
      '--server',
      'https://localhost:8443',
      '--api-key',
      'lw_' + '0'.repeat(64),
      '--insecure',
      'login',
    )

    // ASSERT
    expect(loginCmd.runLogin).toHaveBeenCalledWith({
      server: 'https://localhost:8443',
      apiKey: 'lw_' + '0'.repeat(64),
      insecure: true,
    })
  })

  it('shouldAcceptTheShortGlobalAliases', async () => {
    await parse('-s', 'https://localhost:8443', '-k', 'lw_abc', 'login')

    expect(loginCmd.runLogin).toHaveBeenCalledWith({
      server: 'https://localhost:8443',
      apiKey: 'lw_abc',
      insecure: undefined,
    })
  })

  it('shouldDispatchLogout', async () => {
    await parse('logout')

    expect(logoutCmd.runLogout).toHaveBeenCalledOnce()
  })
})

describe('global options', () => {
  it('shouldReachASubcommandActionThroughOptsWithGlobals', async () => {
    // ARRANGE: the handlers resolve credentials via cmd.optsWithGlobals(), so
    // a flag given before the subcommand must be visible from the leaf.
    await parse('-s', 'https://localhost:8443', '--insecure', 'collections', 'list')

    // ACT
    const args = argsOf(collectionsCmd.runCollectionsList)
    const leaf = args[args.length - 1] as { optsWithGlobals: () => Record<string, unknown> }

    // ASSERT
    expect(leaf.optsWithGlobals()).toMatchObject({
      server: 'https://localhost:8443',
      insecure: true,
    })
  })

  it('shouldAcceptTheCollectionFlagWrittenWithAnEqualsSign', async () => {
    await parse('bookmarks', 'list', '--collection=Work')

    expect(optionsOf(bookmarksCmd.runBookmarksList)['collection']).toBe('Work')
  })
})

describe('completion', () => {
  it('shouldPrintAScriptForEachSupportedShell', async () => {
    // ARRANGE
    const written: string[] = []
    vi.spyOn(process.stdout, 'write').mockImplementation((chunk: string | Uint8Array) => {
      written.push(String(chunk))
      return true
    })

    // ACT
    await parse('completion', 'bash')

    // ASSERT
    expect(written.join('')).toContain('linkweave')
  })

  it('shouldRejectAnUnsupportedShell', async () => {
    await expect(parse('completion', 'csh')).rejects.toThrow(CommanderError)
  })

  it('shouldDispatchTheHiddenCompleteCallbackWithSourceAndPrefix', async () => {
    // ACT
    await parse('__complete', 'tags', 'de', '--collection', 'Work')

    // ASSERT
    const args = argsOf(completeCmd.runComplete)
    expect(args[0]).toBe('tags')
    expect(args[1]).toBe('de')
    expect(args[2]).toEqual({ collection: 'Work' })
  })

  it('shouldAllowTheCompletePrefixToBeOmitted', async () => {
    await parse('__complete', 'collections')

    expect(argsOf(completeCmd.runComplete)[1]).toBeUndefined()
  })

  it('shouldRejectAnUnknownCompletionSource', async () => {
    await expect(parse('__complete', 'bookmarks')).rejects.toThrow(CommanderError)
  })
})

describe('unknown input', () => {
  it('shouldRejectAnUnknownCommand', async () => {
    await expect(parse('bookmark', 'list')).rejects.toThrow(CommanderError)
  })

  it('shouldRejectAnUnknownSubcommand', async () => {
    await expect(parse('bookmarks', 'purge')).rejects.toThrow(CommanderError)
  })
})
