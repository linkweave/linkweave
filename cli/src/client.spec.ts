import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Configuration } from './api'
import type { EffectiveConfig } from './config'
import { NOT_AUTHENTICATED_MESSAGE } from './errors'

const TLS_ENV = 'NODE_TLS_REJECT_UNAUTHORIZED'

let originalTlsEnv: string | undefined
let stderr: string

/**
 * client.ts dedupes the TLS warning with a module-level flag, so tests that
 * care about "warns once" need the module in its initial state.
 *
 * `./api` and `./errors` come back from the same reset graph on purpose: after
 * resetModules the re-imported client holds fresh copies of those classes, and
 * an `instanceof` against the statically imported ones would compare two
 * distinct constructors and fail.
 */
async function freshClient(): Promise<{
  client: typeof import('./client')
  api: typeof import('./api')
  errors: typeof import('./errors')
}> {
  vi.resetModules()
  const [client, api, errors] = await Promise.all([
    import('./client'),
    import('./api'),
    import('./errors'),
  ])
  return { client, api, errors }
}

/** Reaches the Configuration a generated API was built with. */
function configurationOf(api: object): Configuration {
  return (api as unknown as { configuration: Configuration }).configuration
}

beforeEach(() => {
  originalTlsEnv = process.env[TLS_ENV]
  delete process.env[TLS_ENV]
  stderr = ''
  vi.spyOn(process.stderr, 'write').mockImplementation((chunk: string | Uint8Array) => {
    stderr += String(chunk)
    return true
  })
})

afterEach(() => {
  vi.restoreAllMocks()
  if (originalTlsEnv === undefined) delete process.env[TLS_ENV]
  else process.env[TLS_ENV] = originalTlsEnv
})

describe('createClients', () => {
  it('shouldBuildOneClientPerResourceTheCliUses', async () => {
    // ARRANGE
    const {
      client: { createClients },
      api,
    } = await freshClient()

    // ACT
    const clients = createClients('https://test.example', 'lw_test')

    // ASSERT
    expect(clients.auth).toBeInstanceOf(api.AuthResourceApi)
    expect(clients.bookmarks).toBeInstanceOf(api.BookmarkResourceApi)
    expect(clients.collections).toBeInstanceOf(api.CollectionResourceApi)
    expect(clients.export).toBeInstanceOf(api.ExportResourceApi)
    expect(clients.folders).toBeInstanceOf(api.FolderResourceApi)
    expect(clients.import).toBeInstanceOf(api.ImportResourceApi)
    expect(clients.tags).toBeInstanceOf(api.TagResourceApi)
    expect(clients.trash).toBeInstanceOf(api.TrashbinResourceApi)
  })

  it('shouldSendTheApiKeyAsTheXApiKeyHeader', async () => {
    // ARRANGE
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    const clients = createClients('https://test.example', 'lw_secret')

    // ASSERT
    expect(configurationOf(clients.bookmarks).headers).toEqual({ 'X-API-Key': 'lw_secret' })
  })

  it('shouldUseTheGivenServerAsTheBasePathWithoutAppendingApi', async () => {
    // ARRANGE: the generated operation paths already start with /api, so a
    // basePath that repeated it would produce /api/api/bookmarks.
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    const clients = createClients('https://test.example', 'lw_test')

    // ASSERT
    expect(configurationOf(clients.auth).basePath).toBe('https://test.example')
  })

  it('shouldShareOneConfigurationAcrossEveryClient', async () => {
    // ARRANGE
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    const clients = createClients('https://test.example', 'lw_test')

    // ASSERT
    expect(configurationOf(clients.tags)).toBe(configurationOf(clients.folders))
  })
})

describe('TLS policy', () => {
  it('shouldLeaveCertificateVerificationAloneByDefault', async () => {
    // ARRANGE
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    createClients('https://test.example', 'lw_test')

    // ASSERT
    expect(process.env[TLS_ENV]).toBeUndefined()
    expect(stderr).toBe('')
  })

  it('shouldDisableCertificateVerificationForThisProcessWhenInsecure', async () => {
    // ARRANGE
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    createClients('https://localhost:8443', 'lw_test', true)

    // ASSERT — node reads this per request, so setting it before the first
    // call is enough.
    expect(process.env[TLS_ENV]).toBe('0')
  })

  it('shouldWarnOnStderrSoPipedStdoutStaysClean', async () => {
    // ARRANGE
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    createClients('https://localhost:8443', 'lw_test', true)

    // ASSERT
    expect(stderr).toContain('TLS verification disabled')
  })

  it('shouldWarnOnlyOncePerProcessEvenAcrossSeveralClientBuilds', async () => {
    // ARRANGE: completion and command paths can both build clients in one
    // invocation; repeating the banner would be noise.
    const {
      client: { createClients },
    } = await freshClient()

    // ACT
    createClients('https://localhost:8443', 'lw_test', true)
    createClients('https://localhost:8443', 'lw_test', true)

    // ASSERT
    expect(stderr.match(/TLS verification disabled/g)).toHaveLength(1)
  })

  it('shouldStillApplyTheEnvVarOnASecondInsecureBuildAfterTheWarningIsSuppressed', async () => {
    // ARRANGE: the dedup guard returns early — it must do so *after* setting
    // the variable, or a later client would verify certificates again.
    const {
      client: { createClients },
    } = await freshClient()
    createClients('https://localhost:8443', 'lw_test', true)
    delete process.env[TLS_ENV]

    // ACT
    createClients('https://localhost:8443', 'lw_test', true)

    // ASSERT
    expect(process.env[TLS_ENV]).toBe('0')
  })
})

describe('createAuthenticatedClients', () => {
  it('shouldRefuseToBuildClientsWithoutAnApiKey', async () => {
    // ARRANGE: UC-079 A1 — the user has never run login and passed no key.
    const {
      client: { createAuthenticatedClients },
      errors,
    } = await freshClient()
    const config: EffectiveConfig = { server: 'https://test.example' }

    // ACT
    const error = ((): unknown => {
      try {
        createAuthenticatedClients(config)
        return undefined
      } catch (e) {
        return e
      }
    })()

    // ASSERT
    expect(error).toBeInstanceOf(errors.CliError)
    expect((error as Error).message).toBe(NOT_AUTHENTICATED_MESSAGE)
  })

  it('shouldTreatAnEmptyApiKeyAsMissing', async () => {
    const {
      client: { createAuthenticatedClients },
    } = await freshClient()

    expect(() => createAuthenticatedClients({ server: 'https://test.example', apiKey: '' })).toThrow(
      NOT_AUTHENTICATED_MESSAGE,
    )
  })

  it('shouldBuildClientsFromTheEffectiveConfig', async () => {
    // ARRANGE
    const {
      client: { createAuthenticatedClients },
    } = await freshClient()

    // ACT
    const clients = createAuthenticatedClients({
      server: 'https://test.example',
      apiKey: 'lw_test',
    })

    // ASSERT
    expect(configurationOf(clients.auth).basePath).toBe('https://test.example')
    expect(process.env[TLS_ENV]).toBeUndefined()
  })

  it('shouldCarryTheStoredInsecureFlagIntoTheTlsPolicy', async () => {
    // ARRANGE: `login --insecure` persists the flag, so a later invocation
    // without any flag must still skip verification for that server.
    const {
      client: { createAuthenticatedClients },
    } = await freshClient()

    // ACT
    createAuthenticatedClients({
      server: 'https://localhost:8443',
      apiKey: 'lw_test',
      insecure: true,
    })

    // ASSERT
    expect(process.env[TLS_ENV]).toBe('0')
    expect(stderr).toContain('TLS verification disabled')
  })
})
