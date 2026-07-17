import { Argument, Command, Option } from 'commander'

import pkg from '../package.json'
import { runBookmarksAdd, runBookmarksEdit, runBookmarksList, runBookmarksRm } from './commands/bookmarks'
import { runCollectionsList } from './commands/collections'
import { COMPLETION_SHELLS, completionScript, type CompletionShell } from './commands/completion'
import { runLogin } from './commands/login'
import { runLogout } from './commands/logout'
import { OUTPUT_FORMATS } from './output'

function formatOption(): Option {
  return new Option('-f, --format <format>', 'output format').choices(OUTPUT_FORMATS).default('table')
}

export function buildProgram(): Command {
  const program = new Command()

  program
    .name('linkweave')
    .description('Manage LinkWeave bookmarks from the command line')
    .version(pkg.version, '-v, --version')
    .option('-s, --server <url>', 'LinkWeave API server URL')
    .option('-k, --api-key <key>', 'API key (overrides config file and LINKWEAVE_API_KEY)')
    .option('--insecure', 'disable TLS certificate verification (local development only)')
    .exitOverride()
    .addHelpText(
      'after',
      `
Examples:
  $ linkweave login
  $ linkweave bookmarks add https://example.com --tags reading --folder Inbox
  $ linkweave bookmarks list --format json | jq -r '.[].url'
  $ linkweave -s https://localhost:8443 --insecure collections list

Environment:
  LINKWEAVE_SERVER    server URL (overrides the config file)
  LINKWEAVE_API_KEY   API key (overrides the config file)

Configuration is stored in ~/.linkweave/config.json. Precedence: flags > environment > config file.`,
    )

  program.hook('preAction', () => {
    if (program.opts<{ insecure?: boolean }>().insecure) {
      // UC-079 A7: opt-out for self-signed certs in local development. Node's
      // built-in fetch honors this env var via the tls module defaults.
      process.env['NODE_TLS_REJECT_UNAUTHORIZED'] = '0'
      process.stderr.write('⚠ TLS verification disabled. Only use this with trusted servers.\n')
    }
  })

  program
    .command('login')
    .description('store the server URL and API key in ~/.linkweave/config.json')
    .action(async (_options, cmd: Command) => {
      const { server, apiKey } = cmd.optsWithGlobals<{ server?: string; apiKey?: string }>()
      await runLogin({ server, apiKey })
    })

  program
    .command('logout')
    .description('remove the stored configuration')
    .action(() => runLogout())

  const bookmarks = program.command('bookmarks').description('manage bookmarks')

  bookmarks
    .command('add')
    .description('create a bookmark')
    .argument('<url>', 'the URL to bookmark')
    .option('--title <title>', 'bookmark title (defaults to the URL)')
    .option('--collection <collection>', 'target collection ID or name (defaults to your default collection)')
    .option('--folder <path>', "folder path, e.g. 'Dev/TypeScript' (missing folders are created)")
    .option('--tags <tags>', 'comma-separated tag names (unknown tags are created)')
    .option('--description <description>', 'bookmark description')
    .action(runBookmarksAdd)

  bookmarks
    .command('list')
    .description('list bookmarks in a collection')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .option('--folder <path>', 'only bookmarks in this folder path')
    .option('--tag <tag>', 'only bookmarks with this tag name')
    .addOption(formatOption())
    .action(runBookmarksList)

  bookmarks
    .command('edit')
    .description('update fields of a bookmark')
    .argument('<id>', 'the bookmark ID')
    .option('--title <title>', 'new title')
    .option('--url <url>', 'new URL')
    .option('--description <description>', 'new description')
    .option('--tags <tags>', 'comma-separated tag names replacing the current tags')
    .action(runBookmarksEdit)

  bookmarks
    .command('rm')
    .description('remove a bookmark (moves it to the trashbin)')
    .argument('<id>', 'the bookmark ID')
    .action(async (id: string, _options, cmd: Command) => runBookmarksRm(id, cmd))

  const collections = program.command('collections').description('manage collections')

  collections
    .command('list')
    .description('list your collections')
    .addOption(formatOption())
    .action(runCollectionsList)

  program
    .command('completion')
    .description('print a shell completion script (see README for setup)')
    .addArgument(new Argument('<shell>', 'target shell').choices(COMPLETION_SHELLS))
    .action((shell: CompletionShell, _options, cmd: Command) => {
      process.stdout.write(completionScript(shell, cmd.parent ?? cmd))
    })

  return program
}
