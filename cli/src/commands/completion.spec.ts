import { spawnSync } from 'node:child_process'
import { describe, expect, it } from 'vitest'

import { buildProgram } from '../program'
import { completionScript } from './completion'

const program = buildProgram()

/** Sources the bash script and simulates completing the last (empty) word. */
function bashComplete(line: string[]): string[] {
  const script = completionScript('bash', program)
  const probe = `${script}
COMP_WORDS=(${line.map((word) => `'${word}'`).join(' ')})
COMP_CWORD=${line.length - 1}
_linkweave
printf '%s\\n' "\${COMPREPLY[@]}"`
  const result = spawnSync('bash', ['-c', probe], { encoding: 'utf-8' })
  expect(result.stderr).toBe('')
  expect(result.status).toBe(0)
  return result.stdout.split('\n').filter(Boolean)
}

const hasZsh = spawnSync('zsh', ['--version']).status === 0

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
