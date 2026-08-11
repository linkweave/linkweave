import type { Command } from 'commander'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ApiClients } from '../client'
import type { EffectiveConfig } from '../config'
import { FetchError, ResponseError } from '../api'
import { CliError, EXIT_ERROR, EXIT_USAGE } from '../errors'

const resolveEffectiveConfig = vi.fn()
const loadStoredConfig = vi.fn()
const resolveCollectionId = vi.fn()
const createInterface = vi.fn()

vi.mock('../config', () => ({ resolveEffectiveConfig, loadStoredConfig }))
vi.mock('../resolve', () => ({ resolveCollectionId }))
vi.mock('node:readline/promises', () => ({ createInterface }))

const {
  COLLECTION_FORBIDDEN_MESSAGE,
  confirmIrreversible,
  effectiveConfig,
  resolveTargetCollectionId,
  withHttpErrors,
} = await import('./commandHelpers')

const CONFIG: EffectiveConfig = { server: 'https://test.example', apiKey: 'lw_test' }

/** A double for the clients bag; only the two APIs used here are populated. */
function clientsWith(defaultCollectionId: string): ApiClients {
  return {
    auth: { apiAuthMeGet: vi.fn().mockResolvedValue({ defaultCollectionId }) },
    collections: {},
  } as unknown as ApiClients
}

beforeEach(() => {
  vi.clearAllMocks()
  resolveEffectiveConfig.mockReturnValue(CONFIG)
  loadStoredConfig.mockReturnValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('effectiveConfig', () => {
  it('shouldMergeGlobalFlagsWithTheEnvironmentAndTheStoredFile', async () => {
    // ARRANGE
    const stored = { server: 'https://stored.example', apiKey: 'lw_stored' }
    loadStoredConfig.mockReturnValue(stored)
    const cmd = {
      optsWithGlobals: () => ({ server: 'https://flag.example', apiKey: 'lw_flag', insecure: true }),
    } as unknown as Command

    // ACT
    effectiveConfig(cmd)

    // ASSERT
    expect(resolveEffectiveConfig).toHaveBeenCalledWith(
      { server: 'https://flag.example', apiKey: 'lw_flag', insecure: true },
      process.env,
      stored,
    )
  })

  it('shouldForwardOnlyTheThreeConfigFlagsAndDropCommandSpecificOnes', async () => {
    // ARRANGE: optsWithGlobals also carries --format, --collection and friends;
    // passing those through would let a command option shadow a config field.
    const cmd = {
      optsWithGlobals: () => ({ server: 'https://flag.example', format: 'json', tag: 'dev' }),
    } as unknown as Command

    // ACT
    effectiveConfig(cmd)

    // ASSERT
    expect(resolveEffectiveConfig.mock.calls[0]![0]).toEqual({
      server: 'https://flag.example',
      apiKey: undefined,
      insecure: undefined,
    })
  })
})

describe('withHttpErrors', () => {
  it('shouldReturnTheResultWhenTheCallSucceeds', async () => {
    await expect(withHttpErrors(CONFIG, {}, async () => 'ok')).resolves.toBe('ok')
  })

  it('shouldUseTheCallersForbiddenMessageOnHttp403', async () => {
    // ARRANGE
    const failing = async (): Promise<never> => {
      throw new ResponseError(new Response('', { status: 403 }), 'Response returned an error code')
    }

    // ACT
    const error = await withHttpErrors(
      CONFIG,
      { forbidden: COLLECTION_FORBIDDEN_MESSAGE },
      failing,
    ).catch((e: unknown) => e)

    // ASSERT
    expect(error).toBeInstanceOf(CliError)
    expect((error as CliError).message).toBe(COLLECTION_FORBIDDEN_MESSAGE)
    expect((error as CliError).exitCode).toBe(EXIT_ERROR)
  })

  it('shouldUseTheCallersNotFoundMessageOnHttp404', async () => {
    // ARRANGE
    const failing = async (): Promise<never> => {
      throw new ResponseError(new Response('', { status: 404 }), 'Response returned an error code')
    }

    // ACT & ASSERT
    await expect(
      withHttpErrors(CONFIG, { notFound: 'Bookmark not found: b1' }, failing),
    ).rejects.toThrow('Bookmark not found: b1')
  })

  it('shouldNameTheServerFromTheEffectiveConfigOnANetworkFailure', async () => {
    // ARRANGE: the unreachable-server message quotes the URL actually used, so
    // it must come from the resolved config rather than a default.
    const failing = async (): Promise<never> => {
      throw new FetchError(new Error('ECONNREFUSED'), 'The request failed')
    }

    // ACT & ASSERT
    await expect(
      withHttpErrors({ ...CONFIG, server: 'https://localhost:8443' }, {}, failing),
    ).rejects.toThrow('Cannot reach LinkWeave server at https://localhost:8443')
  })

  it('shouldPassACliErrorThroughWithItsExitCodeIntact', async () => {
    // ARRANGE: a usage error raised inside the callback (e.g. an unknown tag
    // name) must keep exit code 2 rather than be flattened to 1.
    const failing = async (): Promise<never> => {
      throw new CliError('Invalid format', EXIT_USAGE)
    }

    // ACT
    const error = await withHttpErrors(CONFIG, {}, failing).catch((e: unknown) => e)

    // ASSERT
    expect((error as CliError).exitCode).toBe(EXIT_USAGE)
  })
})

describe('resolveTargetCollectionId', () => {
  it('shouldResolveAnExplicitCollectionSpecAndIgnoreTheStoredDefault', async () => {
    // ARRANGE
    const clients = clientsWith('from-server')
    resolveCollectionId.mockResolvedValue('from-flag')

    // ACT
    const id = await resolveTargetCollectionId(
      clients,
      { ...CONFIG, defaultCollectionId: 'from-config' },
      'Work',
    )

    // ASSERT
    expect(id).toBe('from-flag')
    expect(resolveCollectionId).toHaveBeenCalledWith(clients.collections, 'Work')
    expect(clients.auth.apiAuthMeGet).not.toHaveBeenCalled()
  })

  it('shouldPreferTheStoredDefaultOverAnExtraRoundTrip', async () => {
    // ARRANGE
    const clients = clientsWith('from-server')

    // ACT
    const id = await resolveTargetCollectionId(
      clients,
      { ...CONFIG, defaultCollectionId: 'from-config' },
      undefined,
    )

    // ASSERT
    expect(id).toBe('from-config')
    expect(clients.auth.apiAuthMeGet).not.toHaveBeenCalled()
  })

  it('shouldFallBackToTheServerDefaultWhenNothingIsStored', async () => {
    // ARRANGE: this is the path taken when credentials come from --api-key or
    // LINKWEAVE_API_KEY, where no default collection was ever written to disk.
    const clients = clientsWith('from-server')

    // ACT
    const id = await resolveTargetCollectionId(clients, CONFIG, undefined)

    // ASSERT
    expect(id).toBe('from-server')
    expect(clients.auth.apiAuthMeGet).toHaveBeenCalledOnce()
    expect(resolveCollectionId).not.toHaveBeenCalled()
  })

  it('shouldTreatAnEmptyCollectionSpecAsAbsent', async () => {
    // ARRANGE: commander yields '' for `--collection=`; resolving that would
    // fail with "no collection named ''" instead of using the default.
    const clients = clientsWith('from-server')

    // ACT
    const id = await resolveTargetCollectionId(
      clients,
      { ...CONFIG, defaultCollectionId: 'from-config' },
      '',
    )

    // ASSERT
    expect(id).toBe('from-config')
    expect(resolveCollectionId).not.toHaveBeenCalled()
  })
})

describe('confirmIrreversible', () => {
  let isTTY: boolean | undefined
  let question: ReturnType<typeof vi.fn>
  let close: ReturnType<typeof vi.fn>

  beforeEach(() => {
    isTTY = process.stdin.isTTY
    question = vi.fn().mockResolvedValue('y')
    close = vi.fn()
    createInterface.mockReturnValue({ question, close })
  })

  afterEach(() => {
    process.stdin.isTTY = isTTY as boolean
  })

  it('shouldSkipThePromptEntirelyWhenYesWasPassed', async () => {
    // ARRANGE: scripts pass --yes; there must be no read of stdin at all.
    process.stdin.isTTY = false

    // ACT
    await confirmIrreversible('Delete everything?', true)

    // ASSERT
    expect(createInterface).not.toHaveBeenCalled()
  })

  it('shouldRefuseRatherThanAssumeConsentWithoutATty', async () => {
    // ARRANGE: stdin is a pipe — nobody is there to answer.
    process.stdin.isTTY = false

    // ACT
    const error = await confirmIrreversible('Delete everything?', false).catch((e: unknown) => e)

    // ASSERT
    expect(error).toBeInstanceOf(CliError)
    expect((error as CliError).message).toMatch(/pass --yes to confirm/)
    expect((error as CliError).exitCode).toBe(EXIT_USAGE)
    expect(createInterface).not.toHaveBeenCalled()
  })

  it('shouldProceedOnY', async () => {
    process.stdin.isTTY = true

    await expect(confirmIrreversible('Delete everything?', false)).resolves.toBeUndefined()
  })

  it.each(['y', 'Y', 'yes', 'YES', ' yes '])('shouldAccept%pAsConfirmation', async (answer) => {
    // ARRANGE
    process.stdin.isTTY = true
    question.mockResolvedValue(answer)

    // ACT & ASSERT
    await expect(confirmIrreversible('Delete everything?', false)).resolves.toBeUndefined()
  })

  it.each(['n', 'no', '', 'yep', 'sure'])('shouldAbortOn%p', async (answer) => {
    // ARRANGE: anything but an explicit yes aborts — a bare Enter must not
    // destroy data.
    process.stdin.isTTY = true
    question.mockResolvedValue(answer)

    // ACT & ASSERT
    await expect(confirmIrreversible('Delete everything?', false)).rejects.toThrow('Aborted.')
  })

  it('shouldAskOnStderrSoPipedStdoutStaysMachineReadable', async () => {
    // ARRANGE
    process.stdin.isTTY = true

    // ACT
    await confirmIrreversible('Delete everything?', false)

    // ASSERT
    expect(createInterface).toHaveBeenCalledWith({
      input: process.stdin,
      output: process.stderr,
    })
    expect(question).toHaveBeenCalledWith('Delete everything? [y/N]: ')
  })

  it('shouldCloseTheReadlineInterfaceEvenWhenTheUserDeclines', async () => {
    // ARRANGE: a leaked interface keeps the event loop alive and the process
    // hanging after the abort message.
    process.stdin.isTTY = true
    question.mockResolvedValue('n')

    // ACT
    await confirmIrreversible('Delete everything?', false).catch(() => {})

    // ASSERT
    expect(close).toHaveBeenCalledOnce()
  })
})
