import { CommanderError } from 'commander'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { CliError, EXIT_ERROR, EXIT_USAGE } from './errors'

// The exit-code mapping is the unit under test, so the program itself is a
// stub whose parseAsync resolves or throws on demand.
const parseAsync = vi.fn()

vi.mock('./program', () => ({ buildProgram: () => ({ parseAsync }) }))

const { run } = await import('./run')

let stderr: string

/** Collects what run() would have written to stderr. */
const writeError = (chunk: string): void => {
  stderr += chunk
}

beforeEach(() => {
  stderr = ''
  parseAsync.mockReset()
  parseAsync.mockResolvedValue(undefined)
})

describe('run', () => {
  it('shouldExitZeroAndStaySilentWhenTheCommandSucceeds', async () => {
    expect(await run(['node', 'linkweave', 'collections', 'list'], writeError)).toBe(0)
    expect(stderr).toBe('')
  })

  it('shouldForwardArgvToTheProgram', async () => {
    // ARRANGE
    const argv = ['node', 'linkweave', 'bookmarks', 'list']

    // ACT
    await run(argv, writeError)

    // ASSERT
    expect(parseAsync).toHaveBeenCalledWith(argv)
  })

  it('shouldExitZeroOnHelpAndVersion', async () => {
    // ARRANGE: commander throws with exitCode 0 after printing help or the
    // version, because the program sets exitOverride().
    parseAsync.mockRejectedValue(
      new CommanderError(0, 'commander.helpDisplayed', '(outputHelp)'),
    )

    // ACT & ASSERT
    expect(await run(['node', 'linkweave', '--help'], writeError)).toBe(0)
  })

  it('shouldExitTwoOnACommanderUsageError', async () => {
    parseAsync.mockRejectedValue(
      new CommanderError(1, 'commander.missingArgument', "error: missing required argument 'url'"),
    )

    expect(await run(['node', 'linkweave', 'bookmarks', 'add'], writeError)).toBe(EXIT_USAGE)
  })

  it('shouldNotRepeatTheMessageCommanderAlreadyPrinted', async () => {
    // ARRANGE: commander writes its own diagnostics before throwing; printing
    // them again would show the user the same error twice.
    parseAsync.mockRejectedValue(
      new CommanderError(1, 'commander.unknownCommand', 'error: unknown command'),
    )

    // ACT
    await run(['node', 'linkweave', 'nope'], writeError)

    // ASSERT
    expect(stderr).toBe('')
  })

  it('shouldReportACliErrorWithItsOwnExitCode', async () => {
    parseAsync.mockRejectedValue(new CliError('Invalid format', EXIT_USAGE))

    expect(await run(['node', 'linkweave'], writeError)).toBe(EXIT_USAGE)
    expect(stderr).toBe('Error: Invalid format\n')
  })

  it('shouldDefaultACliErrorToExitOne', async () => {
    parseAsync.mockRejectedValue(new CliError('Authentication failed.'))

    expect(await run(['node', 'linkweave'], writeError)).toBe(EXIT_ERROR)
    expect(stderr).toBe('Error: Authentication failed.\n')
  })

  it('shouldReportAnUnexpectedErrorAsExitOne', async () => {
    // ARRANGE: a bug rather than a handled failure — the message still reaches
    // the user instead of an unhandled rejection trace.
    parseAsync.mockRejectedValue(new TypeError('x is not a function'))

    // ACT & ASSERT
    expect(await run(['node', 'linkweave'], writeError)).toBe(EXIT_ERROR)
    expect(stderr).toBe('Error: x is not a function\n')
  })

  it('shouldStringifyAThrownNonError', async () => {
    parseAsync.mockRejectedValue('boom')

    expect(await run(['node', 'linkweave'], writeError)).toBe(EXIT_ERROR)
    expect(stderr).toBe('Error: boom\n')
  })
})
