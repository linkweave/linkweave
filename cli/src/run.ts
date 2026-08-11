import { CommanderError } from 'commander'

import { CliError, EXIT_ERROR, EXIT_USAGE } from './errors'
import { buildProgram } from './program'

/** Where user-facing failures are written. */
type ErrorWriter = (chunk: string) => void

const writeOnStderr: ErrorWriter = (chunk) => {
  process.stderr.write(chunk)
}

/**
 * Parses argv, runs the selected command, and returns the exit code the
 * process should end with (UC-079 BR-017).
 *
 * Returning the code rather than setting `process.exitCode` here keeps the
 * whole mapping — CommanderError, CliError, anything else — testable without a
 * child process, and leaves the one place that touches process state in
 * main.ts.
 */
export async function run(
  argv: string[],
  writeError: ErrorWriter = writeOnStderr,
): Promise<number> {
  try {
    await buildProgram().parseAsync(argv)
    return 0
  } catch (error) {
    // Commander already printed its own message (usage error, help, version),
    // so this only picks the code. `--help` and `--version` throw with code 0.
    if (error instanceof CommanderError) {
      return error.exitCode === 0 ? 0 : EXIT_USAGE
    }
    if (error instanceof CliError) {
      writeError(`Error: ${error.message}\n`)
      return error.exitCode
    }
    writeError(`Error: ${error instanceof Error ? error.message : String(error)}\n`)
    return EXIT_ERROR
  }
}
