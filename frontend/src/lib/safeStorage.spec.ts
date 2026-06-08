// @vitest-environment happy-dom
import { vi } from 'vitest'
import { safeGetItem, safeSetItem } from './safeStorage'

describe('safeStorage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  describe('safeGetItem', () => {
    it('should return the stored value', () => {
      localStorage.setItem('k', 'v')
      expect(safeGetItem('k')).toEqual('v')
    })

    it('should return null for a missing key', () => {
      expect(safeGetItem('missing')).toBeNull()
    })

    it('should return null instead of throwing when storage is unavailable', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('unavailable')
      })
      expect(safeGetItem('k')).toBeNull()
    })
  })

  describe('safeSetItem', () => {
    it('should persist the value', () => {
      safeSetItem('k', 'v')
      expect(localStorage.getItem('k')).toEqual('v')
    })

    it('should not throw when storage is unavailable', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('unavailable')
      })
      expect(() => safeSetItem('k', 'v')).not.toThrow()
    })
  })
})
