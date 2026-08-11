// End-to-end tests for the LinkWeave CLI (cli/): drives the built binary as a
// child process against the same API the browser suite uses. No browser is
// involved, so `test` comes straight from @playwright/test rather than
// ./fixtures (which harvests page coverage and would boot Chromium per test).
//
// The CLI talks to the Vite dev server URL; Vite proxies /api to Quarkus. The
// dev cert is self-signed from the CLI's perspective (node ignores the OS
// trust store), so every invocation runs with --insecure — which doubles as
// coverage for UC-079 A7.
import { execFile, execSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import { expect, request as playwrightRequest, test, type APIRequestContext } from '@playwright/test'
import { api } from './helpers/api'
import { loginViaApi, registerTestUser } from './models/TestUser'

const execFileAsync = promisify(execFile)

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'https://local-linkweave.localhost:5173'
const CLI_DIR = path.resolve(process.cwd(), '../cli')
const CLI_DIST = path.join(CLI_DIR, 'dist', 'main.js')

type CliResult = { code: number; stdout: string; stderr: string }

// Shared state: one user + API key for the whole serial describe.
let ctx: APIRequestContext
let apiKey = ''
let defaultCollectionId = ''

async function runCli(args: string[], envOverrides: Record<string, string | undefined> = {}): Promise<CliResult> {
  const env: Record<string, string | undefined> = {
    ...process.env,
    LINKWEAVE_API_KEY: apiKey,
    LINKWEAVE_SERVER: BASE_URL,
    ...envOverrides,
  }
  try {
    const { stdout, stderr } = await execFileAsync('node', [CLI_DIST, '--insecure', ...args], { env })
    return { code: 0, stdout, stderr }
  } catch (error) {
    const failure = error as Partial<CliResult> & { code?: number | string }
    return {
      code: typeof failure.code === 'number' ? failure.code : 1,
      stdout: failure.stdout ?? '',
      stderr: failure.stderr ?? '',
    }
  }
}

/** Parses `bookmarks list --format=json` output. */
function parseBookmarks(stdout: string): Array<{
  id: string
  data: { title: string; url: string; folderId?: string; tagIds?: string[] }
}> {
  return JSON.parse(stdout)
}

test.describe('CLI', () => {
  // The tests build on each other's data (add -> edit -> rm).
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async () => {
    // The e2e CI workflow builds the CLI beforehand; locally we build on
    // demand so `pnpm exec playwright test cli` just works.
    if (!existsSync(CLI_DIST)) {
      execSync('pnpm run build', { cwd: CLI_DIR, stdio: 'inherit' })
    }

    ctx = await playwrightRequest.newContext({ baseURL: BASE_URL, ignoreHTTPSErrors: true })
    const user = await registerTestUser(ctx, 'cli')
    await loginViaApi(ctx, user)
    const me = await api<{ defaultCollectionId: string }>(ctx, 'GET', '/api/auth/me')
    defaultCollectionId = me.defaultCollectionId
    const created = await api<{ key: string }>(ctx, 'POST', '/api/auth/api-keys', {
      name: 'cli-e2e',
    })
    apiKey = created.key
  })

  test.afterAll(async () => {
    // Hard-deletes the throwaway user and everything they own.
    await ctx.delete('/api/auth/me').catch(() => {})
    await ctx.dispose()
  })

  test('should list the default collection', async () => {
    const result = await runCli(['collections', 'list', '--format=json'])

    expect(result.code).toBe(0)
    const ids = JSON.parse(result.stdout).map((c: { id: string }) => c.id)
    expect(ids).toContain(defaultCollectionId)
  })

  let bookmarkId = ''

  test('should add a bookmark with tags and folder', async () => {
    // ACT
    const result = await runCli([
      'bookmarks',
      'add',
      'https://quarkus.io/guides',
      '--title',
      'Quarkus Guides',
      '--tags',
      'dev,java',
      '--folder',
      'Dev/Java',
    ])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Bookmark created: Quarkus Guides (https://quarkus.io/guides)')

    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const bookmark = parseBookmarks(list.stdout).find((b) => b.data.url === 'https://quarkus.io/guides')
    expect(bookmark, 'created bookmark should appear in the list').toBeDefined()
    expect(bookmark!.data.tagIds).toHaveLength(2)
    expect(bookmark!.data.folderId).toBeTruthy()
    bookmarkId = bookmark!.id
  })

  test('should filter the list by tag and by folder', async () => {
    const byTag = await runCli(['bookmarks', 'list', '--tag', 'dev', '--format=ids'])
    expect(byTag.code).toBe(0)
    expect(byTag.stdout).toContain(bookmarkId)

    const byFolder = await runCli(['bookmarks', 'list', '--folder', 'Dev/Java', '--format=ids'])
    expect(byFolder.code).toBe(0)
    expect(byFolder.stdout).toContain(bookmarkId)

    const byOtherTag = await runCli(['bookmarks', 'list', '--tag', 'java', '--format=table'])
    expect(byOtherTag.code).toBe(0)
    expect(byOtherTag.stdout).toContain('Quarkus Guides')
  })

  test('should list the tags that add created', async () => {
    // ACT
    const result = await runCli(['tags', 'list', '--format=json'])

    // ASSERT
    expect(result.code).toBe(0)
    const names = JSON.parse(result.stdout).map((t: { name: string }) => t.name)
    expect(names).toEqual(expect.arrayContaining(['dev', 'java']))

    const table = await runCli(['tags', 'list'])
    expect(table.code).toBe(0)
    expect(table.stdout).toMatch(/^ID\s+Name$/m)
  })

  test('should list folder paths created by add', async () => {
    // ACT
    const result = await runCli(['folders', 'list', '--format=json'])

    // ASSERT — `--folder Dev/Java` creates both levels, and each is listed by
    // the full path that --folder itself accepts.
    expect(result.code).toBe(0)
    const paths = JSON.parse(result.stdout).map((f: { path: string }) => f.path)
    expect(paths).toEqual(expect.arrayContaining(['Dev', 'Dev/Java']))
  })

  test('should edit a bookmark', async () => {
    // ACT
    const result = await runCli(['bookmarks', 'edit', bookmarkId, '--title', 'Renamed via CLI'])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Bookmark updated: Renamed via CLI')

    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const bookmark = parseBookmarks(list.stdout).find((b) => b.id === bookmarkId)
    expect(bookmark!.data.title).toBe('Renamed via CLI')
    // Unspecified fields must survive the update.
    expect(bookmark!.data.tagIds).toHaveLength(2)
  })

  test('should edit the url, description and tags together', async () => {
    // ACT
    const result = await runCli([
      'bookmarks',
      'edit',
      bookmarkId,
      '--url',
      'https://quarkus.io/guides/getting-started',
      '--description',
      'Getting started guide',
      '--tags',
      'reading',
    ])

    // ASSERT
    expect(result.code).toBe(0)

    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const bookmark = parseBookmarks(list.stdout).find((b) => b.id === bookmarkId)
    expect(bookmark!.data.url).toBe('https://quarkus.io/guides/getting-started')
    // --tags replaces the set outright rather than adding to it.
    expect(bookmark!.data.tagIds).toHaveLength(1)
    // The title set by the previous test must survive an edit that omits it.
    expect(bookmark!.data.title).toBe('Renamed via CLI')
  })

  test('should reject an edit that names no field', async () => {
    const result = await runCli(['bookmarks', 'edit', bookmarkId])

    expect(result.code).toBe(2)
    expect(result.stderr).toContain('Nothing to update')
  })

  test('should remove a bookmark', async () => {
    // ACT
    const result = await runCli(['bookmarks', 'rm', bookmarkId])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain(`✓ Bookmark removed: ${bookmarkId}`)

    const list = await runCli(['bookmarks', 'list', '--format=ids'])
    expect(list.stdout).not.toContain(bookmarkId)
  })

  test('should show the removed bookmark in the trash', async () => {
    // ACT
    const result = await runCli(['trash', 'list', '--format=json'])

    // ASSERT — rm is a soft delete, so the bookmark is recoverable.
    expect(result.code).toBe(0)
    const items = JSON.parse(result.stdout) as Array<{ kind: string; id: string; label: string }>
    const trashed = items.find((item) => item.id === bookmarkId)
    expect(trashed, 'removed bookmark should be in the trash').toBeDefined()
    expect(trashed!.kind).toBe('bookmark')
    expect(trashed!.label).toBe('Renamed via CLI')

    const table = await runCli(['trash', 'list'])
    expect(table.stdout).toMatch(/^Type\s+ID\s+Name\s+Deleted$/m)
  })

  test('should restore a bookmark from the trash', async () => {
    // ACT
    const result = await runCli(['trash', 'restore', bookmarkId])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Restored bookmark: Renamed via CLI')

    const list = await runCli(['bookmarks', 'list', '--format=ids'])
    expect(list.stdout).toContain(bookmarkId)

    const trash = await runCli(['trash', 'list', '--format=ids'])
    expect(trash.stdout).not.toContain(bookmarkId)
  })

  test('should refuse to restore something that is not in the trash', async () => {
    const result = await runCli(['trash', 'restore', bookmarkId])

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('is in the trash')
  })

  test('should refuse to purge without a tty unless --yes is passed', async () => {
    // ARRANGE: back into the trash, so there is something to purge.
    await runCli(['bookmarks', 'rm', bookmarkId])

    // ACT — execFile gives the child no TTY, which is exactly the piped-stdin
    // case that must not be read as consent.
    const result = await runCli(['trash', 'purge', bookmarkId])

    // ASSERT
    expect(result.code).toBe(2)
    expect(result.stderr).toContain('pass --yes to confirm')

    const trash = await runCli(['trash', 'list', '--format=ids'])
    expect(trash.stdout, 'refusing must not have deleted anything').toContain(bookmarkId)
  })

  test('should purge one item permanently with --yes', async () => {
    // ACT
    const result = await runCli(['trash', 'purge', bookmarkId, '--yes'])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Permanently deleted bookmark: Renamed via CLI')

    const trash = await runCli(['trash', 'list', '--format=ids'])
    expect(trash.stdout).not.toContain(bookmarkId)
  })

  test('should empty the trash', async () => {
    // ARRANGE: a throwaway bookmark, removed so the trash is non-empty.
    await runCli(['bookmarks', 'add', 'https://example.com/temp', '--title', 'Temp'])
    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const temp = parseBookmarks(list.stdout).find((b) => b.data.url === 'https://example.com/temp')
    await runCli(['bookmarks', 'rm', temp!.id])

    // ACT
    const result = await runCli(['trash', 'empty', '--yes'])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Trash emptied.')

    const trash = await runCli(['trash', 'list'])
    expect(trash.stdout).toContain('The trash is empty.')
  })

  test('should say so rather than fail when emptying an already empty trash', async () => {
    const result = await runCli(['trash', 'empty', '--yes'])

    expect(result.code).toBe(0)
    expect(result.stdout).toContain('The trash is already empty.')
  })

  test('should fail with exit code 1 on an invalid API key', async () => {
    const result = await runCli(['bookmarks', 'list'], {
      LINKWEAVE_API_KEY: 'lw_' + '0'.repeat(64),
    })

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('Authentication failed')
  })

  test('should fail with exit code 2 on usage errors', async () => {
    const result = await runCli(['bookmarks', 'add'])

    expect(result.code).toBe(2)
    expect(result.stderr).toContain('error')
  })

  test('should print a completion script for every supported shell', async () => {
    // ARRANGE: the marker each shell needs for the script to register at all.
    const markers: Record<string, string> = {
      bash: 'complete -F _linkweave linkweave',
      zsh: 'compdef _linkweave linkweave',
      fish: 'complete -c linkweave',
    }

    for (const [shell, marker] of Object.entries(markers)) {
      // ACT
      const result = await runCli(['completion', shell])

      // ASSERT
      expect(result.code, `${shell} completion should succeed`).toBe(0)
      expect(result.stdout, `${shell} script should register itself`).toContain(marker)
      // The script drives the hidden callback; without it nothing completes.
      expect(result.stdout).toContain('__complete')
    }
  })

  test('should reject an unsupported completion shell', async () => {
    const result = await runCli(['completion', 'csh'])

    expect(result.code).toBe(2)
    expect(result.stderr).toContain('csh')
  })

  test('should suggest live values from the hidden completion callback', async () => {
    // ARRANGE: a private cache dir, so the 60s completion cache cannot serve
    // another test's values or leak into the developer's own.
    const cacheHome = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-cache-'))
    try {
      // ACT
      const collections = await runCli(['__complete', 'collections'], {
        XDG_CACHE_HOME: cacheHome,
      })
      const tags = await runCli(['__complete', 'tags'], { XDG_CACHE_HOME: cacheHome })

      // ASSERT — one name per line, and the callback never fails loudly.
      expect(collections.code).toBe(0)
      expect(collections.stdout.split('\n').filter(Boolean).length).toBeGreaterThan(0)
      expect(tags.code).toBe(0)
      expect(tags.stdout).toContain('dev')
    } finally {
      rmSync(cacheHome, { recursive: true, force: true })
    }
  })

  test('should filter completion candidates by the typed prefix', async () => {
    // ARRANGE
    const cacheHome = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-cache-'))
    try {
      // ACT
      const result = await runCli(['__complete', 'tags', 'de'], { XDG_CACHE_HOME: cacheHome })

      // ASSERT
      expect(result.code).toBe(0)
      const names = result.stdout.split('\n').filter(Boolean)
      expect(names).toContain('dev')
      expect(names.every((name) => name.toLowerCase().startsWith('de'))).toBe(true)
    } finally {
      rmSync(cacheHome, { recursive: true, force: true })
    }
  })

  test('should stay silent and succeed when completion cannot authenticate', async () => {
    // ARRANGE: a completion helper that printed an error would corrupt the
    // command line the user is mid-way through typing.
    const cacheHome = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-cache-'))
    try {
      // ACT
      const result = await runCli(['__complete', 'collections'], {
        XDG_CACHE_HOME: cacheHome,
        LINKWEAVE_API_KEY: 'lw_' + '0'.repeat(64),
      })

      // ASSERT
      expect(result.code).toBe(0)
      expect(result.stdout).toBe('')
    } finally {
      rmSync(cacheHome, { recursive: true, force: true })
    }
  })

  test('should show every field of one bookmark', async () => {
    // ARRANGE
    await runCli(['bookmarks', 'add', 'https://vuejs.org', '--title', 'Vue', '--tags', 'dev'])
    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const id = parseBookmarks(list.stdout).find((b) => b.data.url === 'https://vuejs.org')!.id

    // ACT
    const result = await runCli(['bookmarks', 'show', id])

    // ASSERT — tag IDs are resolved to names for the table.
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('Vue')
    expect(result.stdout).toContain('https://vuejs.org')
    expect(result.stdout).toMatch(/Tags\s+dev/)

    const asJson = await runCli(['bookmarks', 'show', id, '--format=json'])
    expect(JSON.parse(asJson.stdout).id).toBe(id)
  })

  test('should report a bookmark that does not exist', async () => {
    const result = await runCli(['bookmarks', 'show', '550e8400-e29b-41d4-a716-446655440999'])

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('Bookmark not found')
  })

  test('should export a collection as a browser bookmarks file and import it back', async () => {
    // ARRANGE: a known bookmark to look for on the way round.
    await runCli(['bookmarks', 'add', 'https://svelte.dev', '--title', 'Svelte', '--folder', 'Frameworks'])

    // ACT — no --output, so the HTML lands on stdout for redirecting.
    const exported = await runCli(['bookmarks', 'export'])

    // ASSERT
    expect(exported.code).toBe(0)
    expect(exported.stdout).toContain('<!DOCTYPE NETSCAPE-Bookmark-file-1>')
    expect(exported.stdout).toContain('https://svelte.dev')

    const dir = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-io-'))
    try {
      // ACT — the same export written to a file, then imported into a second
      // collection: the round trip is the point, not either half alone.
      const file = path.join(dir, 'bookmarks.html')
      const toFile = await runCli(['bookmarks', 'export', '--output', file])
      expect(toFile.code).toBe(0)
      expect(readFileSync(file, 'utf-8')).toBe(exported.stdout)

      await runCli(['collections', 'create', 'Imported'])
      const imported = await runCli(['bookmarks', 'import', file, '--collection', 'Imported'])

      // ASSERT
      expect(imported.code).toBe(0)
      expect(imported.stdout).toContain('✓ Imported')

      const list = await runCli(['bookmarks', 'list', '--collection', 'Imported', '--format=json'])
      const urls = parseBookmarks(list.stdout).map((b) => b.data.url)
      expect(urls).toContain('https://svelte.dev')

      const folders = await runCli(['folders', 'list', '--collection', 'Imported', '--format=json'])
      const paths = JSON.parse(folders.stdout).map((f: { path: string }) => f.path)
      expect(paths, 'the folder structure should survive the round trip').toContain('Frameworks')
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('should reject an import file that is not HTML', async () => {
    const dir = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-io-'))
    try {
      // ARRANGE
      const file = path.join(dir, 'links.json')
      writeFileSync(file, '[]')

      // ACT
      const result = await runCli(['bookmarks', 'import', file])

      // ASSERT — refused locally, without spending the upload.
      expect(result.code).toBe(2)
      expect(result.stderr).toContain('is not a bookmarks HTML file')
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })

  test('should create a folder and its missing parents', async () => {
    // ACT
    const result = await runCli(['folders', 'create', 'Langs/Rust/Async'])

    // ASSERT
    expect(result.code).toBe(0)
    const list = await runCli(['folders', 'list', '--format=json'])
    const paths = JSON.parse(list.stdout).map((f: { path: string }) => f.path)
    expect(paths).toEqual(expect.arrayContaining(['Langs', 'Langs/Rust', 'Langs/Rust/Async']))
  })

  test('should refuse to create a folder that already exists', async () => {
    const result = await runCli(['folders', 'create', 'Langs/Rust'])

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('Folder already exists')
  })

  test('should rename a folder without moving it to the top level', async () => {
    // ACT
    const result = await runCli(['folders', 'rename', 'Langs/Rust/Async', 'Tokio'])

    // ASSERT — the update endpoint reads an absent parentId as "move to the
    // root", so this is the regression that matters: the folder must keep its
    // place in the tree rather than reappear as a top-level folder.
    expect(result.code).toBe(0)
    const list = await runCli(['folders', 'list', '--format=json'])
    const paths = JSON.parse(list.stdout).map((f: { path: string }) => f.path)
    expect(paths).toContain('Langs/Rust/Tokio')
    expect(paths).not.toContain('Tokio')
    expect(paths).not.toContain('Langs/Rust/Async')
  })

  test('should reject a folder rename that contains a slash', async () => {
    const result = await runCli(['folders', 'rename', 'Langs/Rust', 'a/b'])

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('folders mv')
  })

  test('should move a folder under a new parent and back to the top level', async () => {
    // ACT
    const moved = await runCli(['folders', 'mv', 'Langs/Rust/Tokio', 'Langs'])

    // ASSERT
    expect(moved.code).toBe(0)
    let paths = JSON.parse(
      (await runCli(['folders', 'list', '--format=json'])).stdout,
    ).map((f: { path: string }) => f.path)
    expect(paths).toContain('Langs/Tokio')

    // ACT — '/' means the collection root.
    const toRoot = await runCli(['folders', 'mv', 'Langs/Tokio', '/'])

    // ASSERT
    expect(toRoot.code).toBe(0)
    paths = JSON.parse((await runCli(['folders', 'list', '--format=json'])).stdout).map(
      (f: { path: string }) => f.path,
    )
    expect(paths).toContain('Tokio')
  })

  test('should refuse to move a folder into its own subfolder', async () => {
    const result = await runCli(['folders', 'mv', 'Langs', 'Langs/Rust'])

    expect(result.code).toBe(1)
    expect(result.stderr).toContain('own subfolder')
  })

  test('should remove a folder into the trashbin', async () => {
    // ACT
    const result = await runCli(['folders', 'rm', 'Tokio'])

    // ASSERT — soft, so it is recoverable and needs no prompt.
    expect(result.code).toBe(0)
    const trash = await runCli(['trash', 'list', '--format=json'])
    const kinds = JSON.parse(trash.stdout) as Array<{ kind: string; label: string }>
    expect(kinds.some((item) => item.kind === 'folder' && item.label === 'Tokio')).toBe(true)
  })

  test('should take a folder’s bookmarks to the trash with it, and bring them back', async () => {
    // ARRANGE: a folder with a bookmark inside it.
    await runCli(['folders', 'create', 'Doomed'])
    await runCli([
      'bookmarks',
      'add',
      'https://example.com/doomed',
      '--title',
      'Doomed link',
      '--folder',
      'Doomed',
    ])

    // ACT
    const removed = await runCli(['folders', 'rm', 'Doomed'])

    // ASSERT — the cascade is what the command's own message promises.
    expect(removed.code).toBe(0)
    const trashed = JSON.parse((await runCli(['trash', 'list', '--format=json'])).stdout) as Array<{
      kind: string
      id: string
      label: string
    }>
    expect(trashed.some((i) => i.kind === 'bookmark' && i.label === 'Doomed link')).toBe(true)
    const folderEntry = trashed.find((i) => i.kind === 'folder' && i.label === 'Doomed')
    expect(folderEntry).toBeDefined()

    // ACT — restoring the folder restores what went down with it.
    const restored = await runCli(['trash', 'restore', folderEntry!.id])

    // ASSERT
    expect(restored.code).toBe(0)
    const list = await runCli(['bookmarks', 'list', '--format=json'])
    const back = parseBookmarks(list.stdout).find((b) => b.data.url === 'https://example.com/doomed')
    expect(back, 'the contained bookmark should come back too').toBeDefined()
  })

  test('should rename a tag', async () => {
    // ACT
    const result = await runCli(['tags', 'rename', 'java', 'jvm'])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain('✓ Tag renamed: java → jvm')

    const names = JSON.parse((await runCli(['tags', 'list', '--format=json'])).stdout).map(
      (t: { name: string }) => t.name,
    )
    expect(names).toContain('jvm')
    expect(names).not.toContain('java')
  })

  test('should refuse to delete a tag without a tty unless --yes is passed', async () => {
    // ACT
    const result = await runCli(['tags', 'rm', 'jvm'])

    // ASSERT — deleting a tag cannot be undone, so it will not assume consent.
    expect(result.code).toBe(2)
    expect(result.stderr).toContain('pass --yes to confirm')
  })

  test('should delete a tag with --yes', async () => {
    // ACT
    const result = await runCli(['tags', 'rm', 'jvm', '--yes'])

    // ASSERT
    expect(result.code).toBe(0)
    const names = JSON.parse((await runCli(['tags', 'list', '--format=json'])).stdout).map(
      (t: { name: string }) => t.name,
    )
    expect(names).not.toContain('jvm')
  })

  test('should create, rename, set default and delete a collection', async () => {
    // ACT
    const created = await runCli(['collections', 'create', 'Scratch'])

    // ASSERT
    expect(created.code).toBe(0)
    expect(created.stdout).toContain('✓ Collection created: Scratch')

    // ACT
    const renamed = await runCli(['collections', 'rename', 'Scratch', 'Sandbox'])

    // ASSERT
    expect(renamed.code).toBe(0)
    let names = JSON.parse((await runCli(['collections', 'list', '--format=json'])).stdout).map(
      (c: { name: string }) => c.name,
    )
    expect(names).toContain('Sandbox')
    expect(names).not.toContain('Scratch')

    // ACT — making it the default, then handing the default back, so the
    // delete below is not blocked by "cannot delete your last collection".
    const madeDefault = await runCli(['collections', 'default', 'Sandbox'])
    expect(madeDefault.code).toBe(0)
    const collections = JSON.parse(
      (await runCli(['collections', 'list', '--format=json'])).stdout,
    ) as Array<{ id: string; name: string; isDefault: boolean }>
    expect(collections.find((c) => c.name === 'Sandbox')?.isDefault).toBe(true)

    const original = collections.find((c) => c.name !== 'Sandbox')!
    await runCli(['collections', 'default', original.id])

    // ACT
    const deleted = await runCli(['collections', 'rm', 'Sandbox', '--yes'])

    // ASSERT
    expect(deleted.code).toBe(0)
    names = JSON.parse((await runCli(['collections', 'list', '--format=json'])).stdout).map(
      (c: { name: string }) => c.name,
    )
    expect(names).not.toContain('Sandbox')
  })

  test('should refuse to delete a collection without a tty unless --yes is passed', async () => {
    const result = await runCli(['collections', 'rm', 'cli-e2e-nonexistent'])

    // Resolution fails before the prompt is ever reached.
    expect(result.code).toBe(1)
    expect(result.stderr).toContain('No collection found matching')
  })

  test('should refuse a rename and a delete from a non-owner', async () => {
    // ARRANGE: a second user, made ADMIN on the owner's collection. Renaming
    // is owner-only and the server enforces it by keeping the old name rather
    // than refusing — a 200 that changed nothing — so the CLI has to decide
    // this from the role, and say ownership rather than echo the name back.
    const guestCtx = await playwrightRequest.newContext({
      baseURL: BASE_URL,
      ignoreHTTPSErrors: true,
    })
    try {
      const guest = await registerTestUser(guestCtx, 'cliguest')
      await loginViaApi(guestCtx, guest)
      const guestKey = await api<{ key: string }>(guestCtx, 'POST', '/api/auth/api-keys', {
        name: 'cli-e2e-guest',
      })
      await api(ctx, 'POST', `/api/collections/${defaultCollectionId}/members`, {
        email: guest.email,
        role: 'ADMIN',
      })
      const asGuest = { LINKWEAVE_API_KEY: guestKey.key }

      // ACT
      const renamed = await runCli(
        ['collections', 'rename', defaultCollectionId, 'Hijacked'],
        asGuest,
      )

      // ASSERT
      expect(renamed.code).toBe(1)
      expect(renamed.stderr).toContain('restricted to its owner')
      expect(renamed.stdout).not.toContain('✓')

      // ACT — and no confirmation prompt on the way to a delete that the
      // server would have refused with a 403 anyway.
      const removed = await runCli(['collections', 'rm', defaultCollectionId, '--yes'], asGuest)

      // ASSERT
      expect(removed.code).toBe(1)
      expect(removed.stderr).toContain('restricted to its owner')

      // ASSERT — the owner's collection is untouched by either attempt.
      const stillThere = JSON.parse(
        (await runCli(['collections', 'list', '--format=json'])).stdout,
      ) as Array<{ id: string; name: string }>
      expect(stillThere.find((c) => c.id === defaultCollectionId)?.name).not.toBe('Hijacked')
    } finally {
      await guestCtx.delete('/api/auth/me').catch(() => {})
      await guestCtx.dispose()
    }
  })

  test('should login non-interactively, use the stored config, and logout', async () => {
    const fakeHome = mkdtempSync(path.join(tmpdir(), 'linkweave-cli-e2e-'))
    try {
      // ACT: store the config in an isolated HOME.
      const login = await runCli(
        ['login', '--api-key', apiKey, '--server', BASE_URL],
        { HOME: fakeHome, USERPROFILE: fakeHome },
      )

      // ASSERT
      expect(login.code).toBe(0)
      expect(login.stdout).toContain('✓ Logged in as')

      // Credentials must now come from the config file, not the environment.
      const list = await runCli(['collections', 'list', '--format=ids'], {
        HOME: fakeHome,
        USERPROFILE: fakeHome,
        LINKWEAVE_API_KEY: undefined,
        LINKWEAVE_SERVER: undefined,
      })
      expect(list.code).toBe(0)
      expect(list.stdout).toContain(defaultCollectionId)

      const logout = await runCli(['logout'], { HOME: fakeHome, USERPROFILE: fakeHome })
      expect(logout.code).toBe(0)
      expect(logout.stdout).toContain('✓ Configuration removed')
    } finally {
      rmSync(fakeHome, { recursive: true, force: true })
    }
  })
})
