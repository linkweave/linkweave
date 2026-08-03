import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ResponseError } from '../api'
import type { CliError } from '../errors'
import type { StoredConfig } from '../config'

const KEY = 'lw_' + 'a'.repeat(64)
const OTHER_KEY = 'lw_' + 'b'.repeat(64)

// Answers the fake readline hands back, in order. Each prompt shifts one.
let answers: string[]
// What the last prompt was asked with, so the tests can check what the user saw.
let questions: string[]
let stored: StoredConfig | undefined
let saved: StoredConfig | undefined
let meResult: (() => Promise<unknown>) | undefined

vi.mock('node:readline/promises', () => ({
  // Faithful on the one point that matters: a real terminal echoes what the
  // user types to the interface's `output`. Echoing here means the muted
  // stream promptHidden installs is actually exercised — with a fake that
  // skipped this, "the key is not echoed" would pass no matter what.
  createInterface: (options: { output?: { write: (chunk: string) => unknown } }) => ({
    question: async (text: string) => {
      questions.push(text)
      const answer = answers.shift() ?? ''
      options.output?.write(`${answer}\n`)
      return answer
    },
    close: () => {},
  }),
}))

vi.mock('../config', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../config')>()
  return {
    ...actual,
    loadStoredConfig: () => stored,
    saveStoredConfig: (config: StoredConfig) => {
      saved = config
    },
    configPath: () => '/xdg/linkweave/config.json',
  }
})

vi.mock('../client', () => ({
  createClients: () => ({
    auth: {
      apiAuthMeGet: () =>
        meResult
          ? meResult()
          : Promise.resolve({ email: 'user@example.com', defaultCollectionId: 'col-1' }),
    },
  }),
}))

const { runLogin } = await import('./loginCmd')

/** Everything written to stderr during a run — prompts, warnings, errors. */
let stderr: string

beforeEach(() => {
  answers = []
  questions = []
  stored = undefined
  saved = undefined
  meResult = undefined
  stderr = ''
  vi.spyOn(process.stderr, 'write').mockImplementation(((chunk: unknown) => {
    stderr += String(chunk)
    return true
  }) as never)
  vi.spyOn(console, 'log').mockImplementation(() => {})
  vi.stubEnv('LINKWEAVE_SERVER', undefined)
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
  Object.defineProperty(process.stdin, 'isTTY', { value: undefined, configurable: true })
})

/** Runs a login expected to fail and returns the CliError it threw. */
async function failureOf(login: Promise<void>): Promise<CliError> {
  try {
    await login
  } catch (e) {
    return e as CliError
  }
  throw new Error("expected the login to fail, but it succeeded")
}

function setTty(isTty: boolean): void {
  Object.defineProperty(process.stdin, 'isTTY', { value: isTty, configurable: true })
}

describe('runLogin — non-interactive', () => {
  it('shouldRefuseWithoutAKeyWhenStdinIsNotATty', async () => {
    // ARRANGE: piped input, e.g. CI. Prompting would hang forever.
    setTty(false)

    // ACT / ASSERT
    await expect(runLogin({})).rejects.toThrow(/API key required in non-interactive mode/)
    expect(saved).toBeUndefined()
  })

  it('shouldRejectAMalformedKeyAsAUsageError', async () => {
    // ACT
    const failure = await failureOf(runLogin({ apiKey: 'lw_nope' }))

    // ASSERT
    expect(failure.message).toMatch(/Invalid API key format/)
    expect(failure.exitCode).toBe(2)
    expect(saved).toBeUndefined()
  })

  it('shouldTrimSurroundingWhitespaceFromAPastedKey', async () => {
    await runLogin({ apiKey: `  ${KEY}\n` })

    expect(saved?.apiKey).toBe(KEY)
  })

  it('shouldStoreTheIdentityTheServerReturns', async () => {
    // ACT
    await runLogin({ apiKey: KEY, server: 'https://x.example/' })

    // ASSERT: BR-024 — the key is validated before anything is written.
    expect(saved).toEqual({
      server: 'https://x.example',
      apiKey: KEY,
      userEmail: 'user@example.com',
      defaultCollectionId: 'col-1',
    })
  })

  it('shouldRejectAnUnusableServerUrlBeforeContactingIt', async () => {
    const failure = await failureOf(runLogin({ apiKey: KEY, server: 'not-a-url' }))

    expect(failure.message).toMatch(/Invalid server URL/)
    expect(saved).toBeUndefined()
  })
})

describe('runLogin — server rejection', () => {
  it('shouldNotStoreAKeyTheServerRefuses', async () => {
    // ARRANGE
    meResult = () =>
      Promise.reject(new ResponseError(new Response('', { status: 401 }), 'unauthorized'))

    // ACT
    const failure = await failureOf(runLogin({ apiKey: KEY }))

    // ASSERT
    expect(failure.message).toMatch(/API key rejected by server/)
    expect(saved).toBeUndefined()
  })

  it('shouldPointAtInsecureWhenTheCertificateIsNotTrusted', async () => {
    meResult = () =>
      Promise.reject(Object.assign(new Error('self-signed'), { code: 'DEPTH_ZERO_SELF_SIGNED_CERT' }))

    const failure = await failureOf(runLogin({ apiKey: KEY }))

    expect(failure.message).toMatch(/Add --insecure/)
    expect(saved).toBeUndefined()
  })
})

describe('runLogin — interactive', () => {
  beforeEach(() => {
    setTty(true)
  })

  it('shouldPromptForServerAndKey', async () => {
    // ARRANGE
    answers = ['https://prompted.example', KEY]

    // ACT
    await runLogin({})

    // ASSERT
    expect(saved?.server).toBe('https://prompted.example')
    expect(saved?.apiKey).toBe(KEY)
  })

  it('shouldKeepTheDefaultServerWhenTheUserJustPressesEnter', async () => {
    // ARRANGE: empty answer means "accept the shown default".
    answers = ['', KEY]

    // ACT
    await runLogin({})

    // ASSERT
    expect(saved?.server).toBe('https://linkweave.dev')
    expect(questions[0]).toBe('LinkWeave server URL [https://linkweave.dev]: ')
  })

  it('shouldOfferTheStoredServerAsTheDefault', async () => {
    stored = { server: 'https://stored.example', apiKey: OTHER_KEY }
    answers = ['', KEY]

    await runLogin({})

    expect(questions[0]).toBe('LinkWeave server URL [https://stored.example]: ')
  })

  it('shouldNotPromptForServerWhenGivenAsAFlag', async () => {
    // ARRANGE: only the key should be asked for.
    answers = [KEY]

    // ACT
    await runLogin({ server: 'https://flag.example' })

    // ASSERT
    expect(questions.some((q) => q.includes('server URL'))).toBe(false)
    expect(questions).toHaveLength(1)
    expect(saved?.server).toBe('https://flag.example')
  })

  it('shouldNeverEchoTheKeyItPrompted', async () => {
    // The key is read through a muted stream so it stays out of the terminal
    // and its scrollback. Nothing the user sees may contain it.
    answers = ['', KEY]

    await runLogin({})

    expect(stderr).not.toContain(KEY)
    expect(saved?.apiKey).toBe(KEY)
  })

  it('shouldRePromptOnAMalformedKeyAndAcceptAGoodOne', async () => {
    // ARRANGE
    answers = ['', 'lw_short', 'still-wrong', KEY]

    // ACT
    await runLogin({})

    // ASSERT: two complaints, then success on the third attempt.
    expect(stderr.match(/Invalid API key format/g)).toHaveLength(2)
    expect(saved?.apiKey).toBe(KEY)
  })

  it('shouldGiveUpAfterThreeMalformedKeys', async () => {
    // ARRANGE: an unattended terminal must not loop forever.
    answers = ['', 'one', 'two', 'three', KEY]

    // ACT
    const failure = await failureOf(runLogin({}))

    // ASSERT: the fourth answer is never consumed.
    expect(failure.message).toMatch(/Invalid API key format/)
    expect(saved).toBeUndefined()
    expect(answers).toEqual([KEY])
  })

  it('shouldTellTheUserWhoseConfigurationIsBeingReplaced', async () => {
    // ARRANGE
    stored = { server: 'https://stored.example', apiKey: OTHER_KEY, userEmail: 'old@example.com' }
    answers = ['', KEY]

    // ACT
    await runLogin({})

    // ASSERT
    expect(stderr).toContain('⚠ Overwriting existing configuration for old@example.com.')
  })

  it('shouldShowWhereTheKeyIsCreatedForTheChosenServer', async () => {
    answers = ['https://prompted.example', KEY]

    await runLogin({})

    expect(stderr).toContain('API key (created at https://prompted.example/settings/api-keys): ')
  })
})
