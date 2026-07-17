import { CommanderError } from 'commander'

import { CliError, EXIT_ERROR, EXIT_USAGE } from './errors'
import { buildProgram } from './program'

async function main(): Promise<void> {
  try {
    await buildProgram().parseAsync(process.argv)
  } catch (error) {
    // Commander already printed its own message (usage error, help, version).
    if (error instanceof CommanderError) {
      process.exit(error.exitCode === 0 ? 0 : EXIT_USAGE)
    }
    if (error instanceof CliError) {
      process.stderr.write(`Error: ${error.message}\n`)
      process.exit(error.exitCode)
    }
    process.stderr.write(`Error: ${error instanceof Error ? error.message : String(error)}\n`)
    process.exit(EXIT_ERROR)
  }
}

void main()
