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
import { existsSync, mkdtempSync, rmSync } from 'node:fs'
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

  test('should remove a bookmark', async () => {
    // ACT
    const result = await runCli(['bookmarks', 'rm', bookmarkId])

    // ASSERT
    expect(result.code).toBe(0)
    expect(result.stdout).toContain(`✓ Bookmark removed: ${bookmarkId}`)

    const list = await runCli(['bookmarks', 'list', '--format=ids'])
    expect(list.stdout).not.toContain(bookmarkId)
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
