import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const deleteStoredConfig = vi.fn()
const configPath = vi.fn(() => '/home/dev/.config/linkweave/config.json')

vi.mock('../config', () => ({ deleteStoredConfig, configPath }))

const { runLogout } = await import('./logoutCmd')

let stdout: string

beforeEach(() => {
  stdout = ''
  vi.clearAllMocks()
  configPath.mockReturnValue('/home/dev/.config/linkweave/config.json')
  vi.spyOn(console, 'log').mockImplementation((...args: unknown[]) => {
    stdout += args.join(' ') + '\n'
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('runLogout', () => {
  it('shouldConfirmRemovalAndPointAtLoginWhenAConfigExisted', async () => {
    // ARRANGE
    deleteStoredConfig.mockReturnValue(true)

    // ACT
    runLogout()

    // ASSERT
    expect(stdout).toContain('✓ Configuration removed.')
    expect(stdout).toContain("Run 'linkweave login' to authenticate again.")
  })

  it('shouldSayWhereItLookedWhenThereWasNothingToRemove', async () => {
    // ARRANGE: naming the path turns "nothing happened" into something the
    // user can act on — most often a different XDG_CONFIG_HOME than they think.
    deleteStoredConfig.mockReturnValue(false)

    // ACT
    runLogout()

    // ASSERT
    expect(stdout).toContain('No configuration found at /home/dev/.config/linkweave/config.json.')
  })

  it('shouldSucceedRatherThanFailWhenThereIsNoConfig', async () => {
    // ARRANGE: logging out twice, or before ever logging in, is not an error —
    // the desired end state is already true.
    deleteStoredConfig.mockReturnValue(false)

    // ACT & ASSERT
    expect(() => runLogout()).not.toThrow()
  })

  it('shouldLetAPermissionFailurePropagate', async () => {
    // ARRANGE: an undeletable config is a real failure; reporting success
    // would leave the API key on disk while telling the user it is gone.
    deleteStoredConfig.mockImplementation(() => {
      throw new Error('Cannot delete /home/dev/.config/linkweave/config.json.')
    })

    // ACT & ASSERT
    expect(() => runLogout()).toThrow('Cannot delete')
    expect(stdout).toBe('')
  })
})
