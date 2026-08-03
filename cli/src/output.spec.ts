import { describe, expect, it } from 'vitest'

import { CliError } from './errors'
import { displayWidth, parseFormat, renderTable } from './output'

describe('parseFormat', () => {
  it('shouldAcceptTheThreeSupportedFormats', () => {
    expect(parseFormat('table')).toBe('table')
    expect(parseFormat('json')).toBe('json')
    expect(parseFormat('ids')).toBe('ids')
  })

  it('shouldRejectUnknownFormatsAsUsageError', () => {
    try {
      parseFormat('yaml')
      expect.unreachable('expected parseFormat to throw')
    } catch (e) {
      expect(e).toBeInstanceOf(CliError)
      expect((e as CliError).exitCode).toBe(2)
    }
  })
})

describe('renderTable', () => {
  it('shouldPadColumnsToTheWidestCell', () => {
    const table = renderTable(
      ['ID', 'Title'],
      [
        ['1', 'Short'],
        ['22', 'A longer title'],
      ],
    )
    expect(table).toBe(
      ['ID  Title', '--  --------------', '1   Short', '22  A longer title'].join('\n'),
    )
  })

  it('shouldTruncateOverlongCells', () => {
    const table = renderTable(['URL'], [['x'.repeat(100)]])
    const row = table.split('\n')[2]!
    expect(row.length).toBe(60)
    expect(row.endsWith('…')).toBe(true)
  })

  it('shouldCollapseControlCharactersThatWouldBreakTheLayout', () => {
    // ARRANGE: titles come from the server and may contain anything.
    const rows = [['1', 'Two\nlines\tand a tab']]

    // ACT
    const table = renderTable(['ID', 'Title'], rows)

    // ASSERT: still exactly one line per row.
    expect(table.split('\n')).toHaveLength(3)
    expect(table).toContain('Two lines and a tab')
  })

  it('shouldStripEscapeSequencesSoTitlesCannotDriveTheTerminal', () => {
    const esc = '\u001B'
    const table = renderTable(['Title'], [[`${esc}[31mred${esc}[0m`]])

    expect(table).not.toContain(esc)
    expect(table).toContain(' [31mred [0m')
  })

  it('shouldAlignColumnsContainingWideCharacters', () => {
    // ARRANGE: each CJK glyph occupies two terminal columns.
    const table = renderTable(
      ['Name', 'Role'],
      [
        ['書籍', 'OWNER'],
        ['abcd', 'VIEWER'],
      ],
    )

    // ACT
    const [, , wide, ascii] = table.split('\n')

    // ASSERT: both name cells are 4 columns wide, so Role starts at the same
    // column on both rows — measured in columns, not UTF-16 indices, which is
    // exactly the distinction the padding has to get right.
    const startColumn = (row: string, role: string): number =>
      displayWidth(row.slice(0, row.indexOf(role)))
    expect(startColumn(wide!, 'OWNER')).toBe(startColumn(ascii!, 'VIEWER'))
    expect(startColumn(wide!, 'OWNER')).toBe(6)
  })
})

describe('displayWidth', () => {
  it('shouldCountTerminalColumnsRatherThanUtf16Length', () => {
    expect(displayWidth('abc')).toBe(3)
    expect(displayWidth('書籍')).toBe(4)
    // One grapheme cluster built from several code points.
    expect(displayWidth('e\u0301')).toBe(1)
  })
})
