import 'fake-indexeddb/auto'
import {
  saveUserInfo,
  loadUserInfo,
  saveCollections,
  loadCollections,
  saveCollectionInfo,
  loadCollectionInfo,
  purgeForUser,
  purgeAll,
  getLastSyncedAt,
} from './offline-cache'
import type { UserInfoJson, CollectionSummaryJson, CollectionInfoJson } from '@/api/generated'

const fakeUser: UserInfoJson = {
  email: 'alice@example.com',
  firstName: 'Alice',
  lastName: 'User',
  roles: new Set(['USER']),
  defaultCollectionId: 'col-1',
}

const fakeCollections: CollectionSummaryJson[] = [
  { id: 'col-1', name: 'Default', isDefault: true, role: 'ADMIN' as CollectionSummaryJson['role'], shared: false },
  { id: 'col-2', name: 'Other', isDefault: false, role: 'READER' as CollectionSummaryJson['role'], shared: false },
]

const fakeCollectionInfo: CollectionInfoJson = {
  id: 'col-1',
  name: 'Default',
  bookmarks: [],
  tags: [],
  folders: [],
  autoTagRules: [],
  propertyDefinitions: [],
}

beforeEach(async () => {
  await purgeAll()
})

describe('offline-cache', () => {
  describe('saveUserInfo / loadUserInfo', () => {
    it('should return null when no user info cached', async () => {
      const result = await loadUserInfo()
      expect(result).toBeNull()
    })

    it('should save and load user info', async () => {
      await saveUserInfo('alice@example.com', fakeUser)
      const result = await loadUserInfo()

      expect(result).not.toBeNull()
      expect(result!.email).toBe('alice@example.com')
      expect(result!.data.email).toBe('alice@example.com')
      expect(result!.data.firstName).toBe('Alice')
      expect(result!.cachedAt).toBeTypeOf('number')
    })

    it('should pick the first user-info entry when multiple exist', async () => {
      await saveUserInfo('bob@example.com', { ...fakeUser, email: 'bob@example.com' })
      await saveUserInfo('alice@example.com', fakeUser)

      const result = await loadUserInfo()
      expect(result).not.toBeNull()
      if (result) {
        expect(['alice@example.com', 'bob@example.com']).toContain(result.email)
      }
    })
  })

  describe('saveCollections / loadCollections', () => {
    it('should return null when no collections cached', async () => {
      const result = await loadCollections('alice@example.com')
      expect(result).toBeNull()
    })

    it('should save and load collections for a user', async () => {
      await saveCollections('alice@example.com', fakeCollections)
      const result = await loadCollections('alice@example.com')

      expect(result).not.toBeNull()
      expect(result).toHaveLength(2)
      const [first, second] = result!
      expect(first!.id).toBe('col-1')
      expect(second!.name).toBe('Other')
    })

    it('should isolate collections between users', async () => {
      await saveCollections('alice@example.com', fakeCollections)
      await saveCollections('bob@example.com', [{ id: 'col-bob', name: 'Bobs Collection', isDefault: false, role: 'ADMIN' as CollectionSummaryJson['role'], shared: false }])

      const aliceResult = await loadCollections('alice@example.com')
      const bobResult = await loadCollections('bob@example.com')

      expect(aliceResult).toHaveLength(2)
      expect(bobResult).toHaveLength(1)
      const [bobCol] = bobResult!
      expect(bobCol!.id).toBe('col-bob')
    })
  })

  describe('saveCollectionInfo / loadCollectionInfo', () => {
    it('should return null when no collection info cached', async () => {
      const result = await loadCollectionInfo('alice@example.com', 'col-1')
      expect(result).toBeNull()
    })

    it('should save and load collection info', async () => {
      await saveCollectionInfo('alice@example.com', fakeCollectionInfo)
      const result = await loadCollectionInfo('alice@example.com', 'col-1')

      expect(result).not.toBeNull()
      if (result) {
        expect(result.id).toBe('col-1')
        expect(result.name).toBe('Default')
      }
    })

    it('should not leak collection info across users', async () => {
      await saveCollectionInfo('alice@example.com', fakeCollectionInfo)
      const result = await loadCollectionInfo('bob@example.com', 'col-1')
      expect(result).toBeNull()
    })
  })

  describe('getLastSyncedAt', () => {
    it('should return null when nothing cached', async () => {
      const result = await getLastSyncedAt('alice@example.com')
      expect(result).toBeNull()
    })

    it('should return the cachedAt timestamp from collections', async () => {
      const before = Date.now()
      await saveCollections('alice@example.com', fakeCollections)
      const result = await getLastSyncedAt('alice@example.com')

      expect(result).toBeGreaterThanOrEqual(before)
      expect(result).toBeLessThanOrEqual(Date.now())
    })
  })

  describe('purgeForUser', () => {
    it('should remove all cached data for a specific user', async () => {
      await saveUserInfo('alice@example.com', fakeUser)
      await saveCollections('alice@example.com', fakeCollections)
      await saveCollectionInfo('alice@example.com', fakeCollectionInfo)

      await saveUserInfo('bob@example.com', { ...fakeUser, email: 'bob@example.com' })

      await purgeForUser('alice@example.com')

      expect(await loadCollections('alice@example.com')).toBeNull()
      expect(await loadCollectionInfo('alice@example.com', 'col-1')).toBeNull()

      const remaining = await loadUserInfo()
      expect(remaining).not.toBeNull()
      expect(remaining!.email).toBe('bob@example.com')
    })
  })

  describe('purgeAll', () => {
    it('should remove all cached data for all users', async () => {
      await saveUserInfo('alice@example.com', fakeUser)
      await saveUserInfo('bob@example.com', { ...fakeUser, email: 'bob@example.com' })
      await saveCollections('alice@example.com', fakeCollections)

      await purgeAll()

      expect(await loadUserInfo()).toBeNull()
      expect(await loadCollections('alice@example.com')).toBeNull()
    })
  })
})
