import { CommanderError } from 'commander'

import { CliError, EXIT_ERROR, EXIT_USAGE } from './errors'
import { buildProgram } from './program'

async function main(): Promise<void> {
  try {
    await buildProgram().parseAsync(process.argv)
  } catch (error) {
    // Setting exitCode rather than calling process.exit(): writes to a piped
    // stdout/stderr are asynchronous on POSIX, and process.exit() discards
    // whatever has not been flushed yet. Returning lets node drain and then
    // exit with the code on its own.
    //
    // Commander already printed its own message (usage error, help, version).
    if (error instanceof CommanderError) {
      process.exitCode = error.exitCode === 0 ? 0 : EXIT_USAGE
      return
    }
    if (error instanceof CliError) {
      process.stderr.write(`Error: ${error.message}\n`)
      process.exitCode = error.exitCode
      return
    }
    process.stderr.write(`Error: ${error instanceof Error ? error.message : String(error)}\n`)
    process.exitCode = EXIT_ERROR
  }
}

void main()
