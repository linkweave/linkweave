import { Argument, Command, Option } from 'commander'

import pkg from '../package.json'
import {
  runBookmarksAdd,
  runBookmarksEdit,
  runBookmarksExport,
  runBookmarksImport,
  runBookmarksList,
  runBookmarksRm,
  runBookmarksShow,
} from './commands/bookmarksCmd'
import {
  runCollectionsCreate,
  runCollectionsList,
  runCollectionsRename,
  runCollectionsRm,
  runCollectionsSetDefault,
} from './commands/collectionsCmd'
import {
  runFoldersCreate,
  runFoldersList,
  runFoldersMv,
  runFoldersRename,
  runFoldersRm,
} from './commands/foldersCmd'
import { runTagsList, runTagsRename, runTagsRm } from './commands/tagsCmd'
import {
  runTrashEmpty,
  runTrashList,
  runTrashPurge,
  runTrashRestore,
} from './commands/trashCmd'
import { COMPLETION_SOURCES, runComplete } from './commands/completeCmd'
import { COMPLETION_SHELLS, completionScript, type CompletionShell } from './commands/completionScriptGenerator'
import { runLogin } from './commands/loginCmd'
import { runOpen } from './commands/openCmd'
import { runSearch } from './commands/searchCmd'
import { runWatch } from './commands/watchCmd'
import { runLogout } from './commands/logoutCmd'
import { configPath } from './config'
import { OUTPUT_FORMATS } from './output'

function formatOption(): Option {
  return new Option('-f, --format <format>', 'output format').choices(OUTPUT_FORMATS).default('table')
}

export function buildProgram(): Command {
  const program = new Command()

  const COLLECTION_SCOPE = 'collection ID or name (defaults to your default collection)'

  program
    .name('linkweave')
    .description('Manage LinkWeave bookmarks from the command line')
    .version(pkg.version, '-v, --version')
    .option('-s, --server <url>', 'LinkWeave API server URL')
    .option('-k, --api-key <key>', 'API key (overrides config file and LINKWEAVE_API_KEY)')
    .option('--insecure', 'disable TLS certificate verification; login stores it for this server')
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

  program
    .command('login')
    .description(`store the server URL and API key in ${configPath()}`)
    .action(async (_options, cmd: Command) => {
      const { server, apiKey, insecure } = cmd.optsWithGlobals<{
        server?: string
        apiKey?: string
        insecure?: boolean
      }>()
      await runLogin({ server, apiKey, insecure })
    })

  program
    .command('search')
    .description('find bookmarks by title, URL or tag name')
    .argument('<query...>', 'words that must all appear somewhere in the bookmark')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .addOption(formatOption())
    .action(runSearch)

  program
    .command('open')
    .description('open a bookmark in your browser')
    .argument('<bookmark...>', 'bookmark ID, or words matching exactly one bookmark')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .option('--print', 'print the URL instead of opening it (records no click)')
    .action(runOpen)

  program
    .command('watch')
    .description('follow a collection\'s changes as they happen (Ctrl-C to stop)')
    .option('--collection <collection>', 'collection ID or name (defaults to your default collection)')
    .option('--retries <n>', 'reconnect attempts before giving up', '6')
    .addOption(formatOption())
    .action(runWatch)

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
    .command('show')
    .description('print every field of one bookmark')
    .argument('<id>', 'the bookmark ID')
    .addOption(formatOption())
    .action(runBookmarksShow)

  bookmarksCmd
    .command('rm')
    .description('remove a bookmark (moves it to the trashbin)')
    .argument('<id>', 'the bookmark ID')
    .action(async (id: string, _options, cmd: Command) => runBookmarksRm(id, cmd))

  bookmarksCmd
    .command('export')
    .description('write a collection as a browser bookmarks HTML file')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .option('-o, --output <file>', 'write to this file instead of stdout')
    .action(runBookmarksExport)

  bookmarksCmd
    .command('import')
    .description('add the bookmarks from a browser HTML export to a collection')
    .argument('<file>', 'a bookmarks .html file')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runBookmarksImport)

  const collectionsCmd = program.command('collections').description('manage collections')

  collectionsCmd
    .command('list')
    .description('list your collections')
    .addOption(formatOption())
    .action(runCollectionsList)

  collectionsCmd
    .command('create')
    .description('create a collection')
    .argument('<name>', 'the collection name')
    .action(runCollectionsCreate)

  collectionsCmd
    .command('rename')
    .description('rename a collection')
    .argument('<collection>', 'collection ID or name')
    .argument('<new-name>', 'the new name')
    .action(runCollectionsRename)

  collectionsCmd
    .command('default')
    .description('make a collection your default')
    .argument('<collection>', 'collection ID or name')
    .action(runCollectionsSetDefault)

  collectionsCmd
    .command('rm')
    .description('delete a collection and everything in it (permanent)')
    .argument('<collection>', 'collection ID or name')
    .option('-y, --yes', 'skip the confirmation prompt')
    .action(runCollectionsRm)

  const tagsCmd = program.command('tags').description('manage tags')

  tagsCmd
    .command('list')
    .description('list the tags in a collection')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .addOption(formatOption())
    .action(runTagsList)

  tagsCmd
    .command('rename')
    .description('rename a tag')
    .argument('<tag>', 'tag ID or name')
    .argument('<new-name>', 'the new name')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runTagsRename)

  tagsCmd
    .command('rm')
    .description('delete a tag and remove it from every bookmark (permanent)')
    .argument('<tag>', 'tag ID or name')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .option('-y, --yes', 'skip the confirmation prompt')
    .action(runTagsRm)

  const foldersCmd = program.command('folders').description('manage folders')

  foldersCmd
    .command('list')
    .description('list folder paths in a collection')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .addOption(formatOption())
    .action(runFoldersList)

  foldersCmd
    .command('create')
    .description('create a folder, and any missing parents, at a path')
    .argument('<path>', "folder path, e.g. 'Dev/TypeScript'")
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runFoldersCreate)

  foldersCmd
    .command('rename')
    .description('rename a folder, leaving it where it is')
    .argument('<path>', 'folder path or ID')
    .argument('<new-name>', 'the new name (no slashes)')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runFoldersRename)

  foldersCmd
    .command('mv')
    .description('move a folder under a different parent')
    .argument('<path>', 'folder path or ID')
    .argument('<destination>', "new parent folder path, or '/' for the top level")
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runFoldersMv)

  foldersCmd
    .command('rm')
    .description('remove a folder and its contents (moves them to the trashbin)')
    .argument('<path>', 'folder path or ID')
    .option('--collection <collection>', COLLECTION_SCOPE)
    .action(runFoldersRm)

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
