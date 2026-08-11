// Runs the generated completion scripts in the real shells.
//
// completionScriptGenerator.spec.ts asserts what the generator *emits*; this
// asserts what the emitted script *does*. The positional-slot arithmetic is
// the part that cannot be checked by reading strings — an off-by-one in the
// index, or a flag value counted as a positional, only shows up when a shell
// executes it.
//
// No server is involved: a stub `linkweave` on PATH answers `__complete` with
// fixed values, which is also what the real script shells out to.
import { execFileSync } from 'node:child_process'
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

import { buildProgram } from '../program'
import { completionScript } from './completionScriptGenerator'

/** Values the stub returns per completion source. */
const FOLDERS = ['Dev', 'Dev/TypeScript', 'Ops']
const TAGS = ['dev', 'java']
const COLLECTIONS = ['Personal', 'Team Reading']

function hasShell(shell: string): boolean {
  try {
    execFileSync('/bin/sh', ['-c', `command -v ${shell}`], { stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}

let dir: string

beforeAll(() => {
  dir = mkdtempSync(join(tmpdir(), 'linkweave-completion-'))

  // Stands in for the installed CLI: `__complete <source> [--collection X]`.
  const stub = join(dir, 'linkweave')
  writeFileSync(
    stub,
    `#!/bin/sh
case "$2" in
  collections) printf '${COLLECTIONS.join('\\n')}\\n' ;;
  tags) printf '${TAGS.join('\\n')}\\n' ;;
  folders) printf '${FOLDERS.join('\\n')}\\n' ;;
esac
`,
    { mode: 0o755 },
  )
  chmodSync(stub, 0o755)

  for (const shell of ['bash', 'zsh', 'fish'] as const) {
    writeFileSync(join(dir, `completion.${shell}`), completionScript(shell, buildProgram()))
  }
})

afterAll(() => {
  rmSync(dir, { recursive: true, force: true })
})

/** Runs a shell snippet with the stub CLI first on PATH. */
function run(shell: string, snippet: string): string {
  return execFileSync(shell, ['-c', snippet], {
    encoding: 'utf-8',
    env: { ...process.env, PATH: `${dir}:${process.env['PATH'] ?? ''}` },
  })
}

/**
 * Completions bash offers for a command line. The trailing empty string is the
 * word under the cursor, exactly as bash builds COMP_WORDS.
 */
function bashCompletions(...words: string[]): string[] {
  const array = words.map((word) => `"${word}"`).join(' ')
  const snippet = `
source "${dir}/completion.bash"
COMP_WORDS=(${array})
COMP_CWORD=${words.length - 1}
_linkweave
printf '%s\\n' "\${COMPREPLY[@]}"
`
  return run('bash', snippet).split('\n').filter(Boolean)
}

/**
 * The zsh script only touches compadd, words and CURRENT, so a stub compadd
 * exercises it without initialising the whole completion system.
 */
function zshCompletions(...words: string[]): string[] {
  const array = words.map((word) => `"${word}"`).join(' ')
  // Both stubs are defined before sourcing: the script calls compdef at the
  // end of the file, which would otherwise be a "command not found" on stderr.
  const snippet = `
compadd() { local a; for a in "$@"; do [[ "$a" == "--" ]] || print -r -- "$a"; done }
compdef() { : }
source "${dir}/completion.zsh"
words=(${array})
CURRENT=${words.length}
_linkweave
`
  return run('zsh', snippet).split('\n').filter(Boolean)
}

/** fish drives its own completion engine for a partial command line. */
function fishCompletions(line: string): string[] {
  const snippet = `source "${dir}/completion.fish"; complete -C ${JSON.stringify(line)}`
  return run('fish', snippet)
    .split('\n')
    .filter(Boolean)
    // fish appends a tab-separated description to each candidate.
    .map((entry) => entry.split('\t')[0]!)
}

const describeBash = hasShell('bash') ? describe : describe.skip
const describeZsh = hasShell('zsh') ? describe : describe.skip
const describeFish = hasShell('fish') ? describe : describe.skip

describeBash('bash completion', () => {
  it('shouldOfferFolderPathsInTheFirstPositionalSlot', () => {
    expect(bashCompletions('linkweave', 'folders', 'mv', '')).toEqual(FOLDERS)
  })

  it('shouldOfferFolderPathsInTheSecondSlotToo', () => {
    // ARRANGE: `folders mv <path> <destination>` completes both.

    // ACT
    const completions = bashCompletions('linkweave', 'folders', 'mv', 'Dev', '')

    // ASSERT
    expect(completions).toEqual(FOLDERS)
  })

  it('shouldStopOfferingValuesPastTheLastPositional', () => {
    // ARRANGE: both slots are filled, so the next word can only be a flag.

    // ACT
    const completions = bashCompletions('linkweave', 'folders', 'mv', 'Dev', 'Ops', '')

    // ASSERT
    expect(completions).not.toContain('Dev/TypeScript')
    expect(completions).toContain('--collection')
  })

  it('shouldNotOfferExistingNamesForARenamesSecondArgument', () => {
    // ARRANGE: `<new-name>` is a name the user is inventing; offering the
    // existing ones would be actively misleading.

    // ACT
    const completions = bashCompletions('linkweave', 'folders', 'rename', 'Dev', '')

    // ASSERT
    expect(completions).not.toContain('Dev/TypeScript')
    expect(completions).toContain('--collection')
  })

  it('shouldNotCountAFlagValueAsAPositional', () => {
    // ARRANGE: `--collection Work` is two words that must not shift the slot.

    // ACT
    const completions = bashCompletions(
      'linkweave',
      'folders',
      'rm',
      '--collection',
      'Work',
      '',
    )

    // ASSERT
    expect(completions).toEqual(FOLDERS)
  })

  it('shouldNotCountAGlobalFlagAsAPositional', () => {
    expect(bashCompletions('linkweave', '--insecure', 'folders', 'mv', '')).toEqual(FOLDERS)
  })

  it('shouldFallBackToFlagsWhenTheWordAlreadyLooksLikeOne', () => {
    // ARRANGE: typing `-` in a positional slot means a flag is wanted.

    // ACT
    const completions = bashCompletions('linkweave', 'folders', 'mv', '-')

    // ASSERT
    expect(completions).not.toContain('Dev')
    expect(completions).toContain('--collection')
  })

  it('shouldOfferTagsForTagCommands', () => {
    expect(bashCompletions('linkweave', 'tags', 'rm', '')).toEqual(TAGS)
  })

  it('shouldOfferCollectionsForCollectionCommands', () => {
    // ARRANGE: names may contain spaces, so the script %q-escapes each one.

    // ACT
    const completions = bashCompletions('linkweave', 'collections', 'rename', '')

    // ASSERT
    expect(completions).toContain('Personal')
    expect(completions.join(' ')).toContain('Team')
  })

  it('shouldStillCompleteSubcommandsAndOptionValues', () => {
    // ARRANGE: the positional dispatch must not disturb what already worked.

    // ACT & ASSERT
    expect(bashCompletions('linkweave', 'folders', '')).toContain('mv')
    expect(bashCompletions('linkweave', 'bookmarks', 'list', '--folder', '')).toEqual(FOLDERS)
    expect(bashCompletions('linkweave', 'bookmarks', 'list', '--format', '')).toContain('json')
  })
})

describeZsh('zsh completion', () => {
  it('shouldOfferFolderPathsInTheFirstPositionalSlot', () => {
    expect(zshCompletions('linkweave', 'folders', 'mv', '')).toEqual(FOLDERS)
  })

  it('shouldOfferFolderPathsInTheSecondSlotToo', () => {
    expect(zshCompletions('linkweave', 'folders', 'mv', 'Dev', '')).toEqual(FOLDERS)
  })

  it('shouldNotOfferExistingNamesForARenamesSecondArgument', () => {
    const completions = zshCompletions('linkweave', 'folders', 'rename', 'Dev', '')

    expect(completions).not.toContain('Dev/TypeScript')
    expect(completions).toContain('--collection')
  })

  it('shouldNotCountAFlagValueAsAPositional', () => {
    expect(zshCompletions('linkweave', 'folders', 'rm', '--collection', 'Work', '')).toEqual(
      FOLDERS,
    )
  })

  it('shouldOfferTagsForTagCommands', () => {
    expect(zshCompletions('linkweave', 'tags', 'rename', '')).toEqual(TAGS)
  })

  it('shouldStillCompleteSubcommands', () => {
    expect(zshCompletions('linkweave', 'folders', '')).toContain('mv')
  })
})

describeFish('fish completion', () => {
  it('shouldOfferFolderPathsInTheFirstPositionalSlot', () => {
    expect(fishCompletions('linkweave folders mv ')).toEqual(expect.arrayContaining(FOLDERS))
  })

  it('shouldOfferFolderPathsInTheSecondSlotToo', () => {
    expect(fishCompletions('linkweave folders mv Dev ')).toEqual(expect.arrayContaining(FOLDERS))
  })

  it('shouldStopOfferingValuesPastTheLastPositional', () => {
    expect(fishCompletions('linkweave folders mv Dev Ops ')).not.toContain('Dev/TypeScript')
  })

  it('shouldNotOfferExistingNamesForARenamesSecondArgument', () => {
    expect(fishCompletions('linkweave folders rename Dev ')).not.toContain('Dev/TypeScript')
  })

  it('shouldNotCountAFlagValueAsAPositional', () => {
    expect(fishCompletions('linkweave folders rm --collection Work ')).toEqual(
      expect.arrayContaining(FOLDERS),
    )
  })

  it('shouldOfferTagsForTagCommands', () => {
    expect(fishCompletions('linkweave tags rm ')).toEqual(expect.arrayContaining(TAGS))
  })

  it('shouldStillCompleteSubcommands', () => {
    expect(fishCompletions('linkweave folders ')).toContain('mv')
  })
})
