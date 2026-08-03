import { Argument, Command, Option } from 'commander'

import pkg from '../package.json'
import { runBookmarksAdd, runBookmarksEdit, runBookmarksList, runBookmarksRm } from './commands/bookmarksCmd'
import { runCollectionsList } from './commands/collectionsCmd'
import { runFoldersList } from './commands/foldersCmd'
import { runTagsList } from './commands/tagsCmd'
import {
  runTrashEmpty,
  runTrashList,
  runTrashPurge,
  runTrashRestore,
} from './commands/trashCmd'
import { COMPLETION_SOURCES, runComplete } from './commands/completeCmd'
import { COMPLETION_SHELLS, completionScript, type CompletionShell } from './commands/completionScriptGenerator'
import { runLogin } from './commands/loginCmd'
import { runLogout } from './commands/logoutCmd'
import { configPath } from './config'
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
  LINKWEAVE_DEBUG     report errors that are otherwise silent (shell completion)

Configuration is stored in ${configPath()} (XDG_CONFIG_HOME).
Precedence: flags > environment > config file.`,
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
    .description(`store the server URL and API key in ${configPath()}`)
    .action(async (_options, cmd: Command) => {
      const { server, apiKey } = cmd.optsWithGlobals<{ server?: string; apiKey?: string }>()
      await runLogin({ server, apiKey })
    })

  program
    .command('logout')
    .description('remove the stored configuration')
    .action(() => runLogout())

  const bookmarksCmd = program.command('bookmarks').description('manage bookmarks')

  bookmarksCmd
    .command('add')
    .description('create a bookmark')
    .argument('<url>', 'the URL to bookmark')
    .option('--title <title>', 'bookmark title (defaults to the URL)')
    .option('--collection <collection>', 'target collection ID or name (defaults to your default collection)')
    .option('--folder <path>', "folder path, e.g. 'Dev/TypeScript' (missing folders are created)")
    .option('--tags <tags>', 'comma-separated tag names (unknown tags are created)')
    .option('--description <description>', 'bookmark description')
    .action(runBookmarksAdd)

  bookmarksCmd
    .command('list')
    .description('list bookmarks in a collection')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .option('--folder <path>', 'only bookmarks in this folder path')
    .option('--tag <tag>', 'only bookmarks with this tag name')
    .addOption(formatOption())
    .action(runBookmarksList)

  bookmarksCmd
    .command('edit')
    .description('update fields of a bookmark')
    .argument('<id>', 'the bookmark ID')
    .option('--title <title>', 'new title')
    .option('--url <url>', 'new URL')
    .option('--description <description>', 'new description')
    .option('--tags <tags>', 'comma-separated tag names replacing the current tags')
    .action(runBookmarksEdit)

  bookmarksCmd
    .command('rm')
    .description('remove a bookmark (moves it to the trashbin)')
    .argument('<id>', 'the bookmark ID')
    .action(async (id: string, _options, cmd: Command) => runBookmarksRm(id, cmd))

  const collectionsCmd = program.command('collections').description('manage collections')

  collectionsCmd
    .command('list')
    .description('list your collections')
    .addOption(formatOption())
    .action(runCollectionsList)

  const tagsCmd = program.command('tags').description('inspect tags')

  tagsCmd
    .command('list')
    .description('list the tags in a collection')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .addOption(formatOption())
    .action(runTagsList)

  const foldersCmd = program.command('folders').description('inspect folders')

  foldersCmd
    .command('list')
    .description('list folder paths in a collection')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .addOption(formatOption())
    .action(runFoldersList)

  const trashCmd = program
    .command('trash')
    .description('inspect and recover soft-deleted items')

  trashCmd
    .command('list')
    .description('list everything in the trashbin')
    .addOption(formatOption())
    .action(runTrashList)

  trashCmd
    .command('restore')
    .description('restore a bookmark or folder from the trashbin')
    .argument('<id>', 'the bookmark or folder ID')
    .action(runTrashRestore)

  trashCmd
    .command('purge')
    .description('permanently delete one item from the trashbin')
    .argument('<id>', 'the bookmark or folder ID')
    .option('-y, --yes', 'skip the confirmation prompt')
    .action(runTrashPurge)

  trashCmd
    .command('empty')
    .description('permanently delete everything in the trashbin')
    .option('-y, --yes', 'skip the confirmation prompt')
    .action(runTrashEmpty)

  program
    .command('completion')
    .description('print a shell completion script (see README for setup)')
    .addArgument(new Argument('<shell>', 'target shell').choices(COMPLETION_SHELLS))
    .action((shell: CompletionShell, _options, cmd: Command) => {
      process.stdout.write(completionScript(shell, cmd.parent ?? cmd))
    })

  // Called by the generated completion scripts, not by users: prints candidate
  // values for an option, one per line, and stays silent on any failure.
  program
    .command('__complete', { hidden: true })
    .addArgument(new Argument('<source>', 'value set').choices(COMPLETION_SOURCES))
    .addArgument(new Argument('[prefix]', 'only values starting with this'))
    .option('--collection <collection>', 'collection scoping tags and folders')
    .action(runComplete)

  return program
}
