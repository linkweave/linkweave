import { spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

import { buildProgram } from '../program'
import { completionScript } from './completion'

const program = buildProgram()

/**
 * A stand-in `linkweave` on PATH, so the dynamic arms can be exercised without
 * a server. It logs its arguments, letting the tests assert what the scripts
 * asked for. Names contain a space on purpose — that is the case the shell
 * quoting has to survive.
 */
let stubDir: string

beforeAll(() => {
  stubDir = mkdtempSync(join(tmpdir(), 'linkweave-stub-'))
  const stub = join(stubDir, 'linkweave')
  writeFileSync(
    stub,
    `#!/bin/bash
if [[ "$1" == "__complete" ]]; then
  echo "$@" >> "${join(stubDir, 'calls.log')}"
  case "$2" in
    collections) printf 'My Links\\nWork\\n' ;;
    tags) printf 'dev\\njava\\n' ;;
    folders) printf 'Dev\\nDev/Java\\n' ;;
  esac
fi
`,
  )
  chmodSync(stub, 0o755)
})

afterAll(() => {
  rmSync(stubDir, { recursive: true, force: true })
})

function stubEnv(): NodeJS.ProcessEnv {
  return { ...process.env, PATH: `${stubDir}:${process.env['PATH'] ?? ''}` }
}

/** Sources the bash script and simulates completing the last (empty) word. */
function bashComplete(line: string[]): string[] {
  const script = completionScript('bash', program)
  const probe = `${script}
COMP_WORDS=(${line.map((word) => `'${word}'`).join(' ')})
COMP_CWORD=${line.length - 1}
_linkweave
printf '%s\\n' "\${COMPREPLY[@]}"`
  const result = spawnSync('bash', ['-c', probe], { encoding: 'utf-8', env: stubEnv() })
  expect(result.stderr).toBe('')
  expect(result.status).toBe(0)
  return result.stdout.split('\n').filter(Boolean)
}

const hasZsh = spawnSync('zsh', ['--version']).status === 0
const hasFish = spawnSync('fish', ['--version']).status === 0

describe('completionScript', () => {
  it('shouldOfferSubcommandsAndGlobalFlagsAtTheRoot', () => {
    const words = bashComplete(['linkweave', ''])
    expect(words).toEqual(expect.arrayContaining(['login', 'logout', 'bookmarks', 'collections', 'completion', '--insecure', '--server']))
  })

  it('shouldOfferNestedSubcommands', () => {
    const words = bashComplete(['linkweave', 'bookmarks', ''])
    expect(words).toEqual(expect.arrayContaining(['add', 'list', 'edit', 'rm', 'help']))
    expect(words).not.toContain('login')
  })

  it('shouldOfferTheFlagsOfTheResolvedSubcommand', () => {
    const words = bashComplete(['linkweave', 'bookmarks', 'add', ''])
    expect(words).toEqual(expect.arrayContaining(['--title', '--collection', '--folder', '--tags', '--description']))
    expect(words).not.toContain('--format')
  })

  it('shouldSkipFlagValuesWhenResolvingTheCommandContext', () => {
    const words = bashComplete(['linkweave', '-s', 'https://localhost:8443', 'bookmarks', ''])
    expect(words).toEqual(expect.arrayContaining(['add', 'list', 'edit', 'rm']))
  })

  it('shouldOfferFormatChoicesAfterTheFormatFlag', () => {
    expect(bashComplete(['linkweave', 'bookmarks', 'list', '--format', ''])).toEqual(['table', 'json', 'ids'])
    expect(bashComplete(['linkweave', 'collections', 'list', '-f', ''])).toEqual(['table', 'json', 'ids'])
  })

  it('shouldOfferShellNamesForTheCompletionCommand', () => {
    const words = bashComplete(['linkweave', 'completion', ''])
    expect(words).toEqual(expect.arrayContaining(['bash', 'zsh', 'fish']))
  })

  it.runIf(hasZsh)('shouldEmitSyntacticallyValidZsh', () => {
    // ARRANGE
    const script = completionScript('zsh', program)

    // ACT
    const result = spawnSync('zsh', ['-fn', '-c', script], { encoding: 'utf-8' })

    // ASSERT
    expect(result.stderr).toBe('')
    expect(result.status).toBe(0)
  })

  it('shouldNotOfferTheHiddenCompleteCommand', () => {
    // __complete exists for the scripts below, never for the user.
    expect(bashComplete(['linkweave', ''])).not.toContain('__complete')
    for (const shell of ['bash', 'zsh', 'fish'] as const) {
      expect(completionScript(shell, program)).not.toContain('-a __complete')
    }
  })

  it('shouldCompleteCollectionNamesFromTheServerKeepingSpacesIntact', () => {
    // ARRANGE / ACT
    const words = bashComplete(['linkweave', 'bookmarks', 'list', '--collection', ''])

    // ASSERT: %q-escaped, so inserting it stays a single shell word.
    expect(words).toEqual(['My\\ Links', 'Work'])
  })

  it('shouldCompleteTagAndFolderValuesFromTheServer', () => {
    expect(bashComplete(['linkweave', 'bookmarks', 'list', '--tag', ''])).toEqual(['dev', 'java'])
    expect(bashComplete(['linkweave', 'bookmarks', 'add', 'https://x', '--folder', ''])).toEqual([
      'Dev',
      'Dev/Java',
    ])
  })

  it('shouldForwardAnAlreadyTypedCollectionWhenCompletingTags', () => {
    // ARRANGE: tags are collection-scoped, so --collection on the line must
    // reach __complete or the wrong tag set comes back.
    const log = join(stubDir, 'calls.log')
    rmSync(log, { force: true })

    // ACT
    bashComplete(['linkweave', 'bookmarks', 'list', '--collection', 'Work', '--tag', ''])

    // ASSERT
    const result = spawnSync('cat', [log], { encoding: 'utf-8' })
    expect(result.stdout).toContain('__complete tags --collection Work')
  })

  it('shouldNotCompleteValuesForTheCommaSeparatedTagsFlag', () => {
    // --tags takes a list; completing it would replace the whole word.
    expect(completionScript('bash', program)).not.toMatch(/--tags\) __linkweave_values/)
  })

  it.runIf(hasFish)('shouldEmitAFishHelperThatSurvivesAnEmptyCommandLine', () => {
    // ARRANGE
    const script = join(stubDir, 'lw.fish')
    writeFileSync(script, completionScript('fish', program))

    // ACT: no command line at all — fish arrays are 1-based, so a naive
    // index-based scan errors here.
    const result = spawnSync('fish', ['-c', `source ${script}; __linkweave_values collections`], {
      encoding: 'utf-8',
      env: stubEnv(),
    })

    // ASSERT
    expect(result.stderr).toBe('')
    expect(result.stdout.split('\n').filter(Boolean)).toEqual(['My Links', 'Work'])
  })

  it.runIf(hasZsh)('shouldEmitAZshHelperThatForwardsTheCollection', () => {
    // ARRANGE
    const script = join(stubDir, 'lw.zsh')
    writeFileSync(script, completionScript('zsh', program))

    // ACT: compadd only exists inside the completion system, so stub it.
    const probe = `source ${script} 2>/dev/null
compadd() { printf '%s\\n' "\${@:2}"; }
words=(linkweave bookmarks list --collection Work --tag '')
CURRENT=7
__linkweave_values tags`
    const result = spawnSync('zsh', ['-f', '-c', probe], { encoding: 'utf-8', env: stubEnv() })

    // ASSERT
    expect(result.stderr).toBe('')
    expect(result.stdout.split('\n').filter(Boolean)).toEqual(['dev', 'java'])
  })

  it('shouldScopeNestedOptionsToTheirFullCommandPathInFish', () => {
    const script = completionScript('fish', program)
    // 'bookmarks list' and 'collections list' share the segment 'list': the
    // --collection filter must require the bookmarks segment as well.
    const collectionLine = script.split('\n').find((line) => line.includes('-l collection') && line.includes('list'))
    expect(collectionLine).toContain('__fish_seen_subcommand_from bookmarks; and __fish_seen_subcommand_from list')
    expect(script).toContain('complete -c linkweave -n __fish_use_subcommand -a login')
    expect(script).toContain('-x -a "table json ids"')
  })
})
