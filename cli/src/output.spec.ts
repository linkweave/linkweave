import { describe, expect, it } from 'vitest'

import { CliError } from './errors'
import { parseFormat, renderTable } from './output'

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
})
