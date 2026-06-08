import { registerStoreReset, resetAllStores } from './storeReset'

describe('storeReset', () => {
  it('should invoke every registered reset handler', () => {
    const a = vi.fn()
    const b = vi.fn()
    registerStoreReset(a)
    registerStoreReset(b)

    resetAllStores()

    expect(a).toHaveBeenCalledTimes(1)
    expect(b).toHaveBeenCalledTimes(1)
  })

  it('should register a handler only once', () => {
    const handler = vi.fn()
    registerStoreReset(handler)
    registerStoreReset(handler)

    resetAllStores()

    expect(handler).toHaveBeenCalledTimes(1)
  })
})
