import type { CollectionInfoJson, CollectionSummaryJson, SavedSearchJson, UserInfoJson } from '@/api/generated'
import {
  CollectionInfoJsonToJSON,
  CollectionSummaryJsonToJSON,
  SavedSearchJsonToJSON,
  UserInfoJsonFromJSON,
  UserInfoJsonToJSON,
} from '@/api/generated'

const DB_NAME = 'linkweave-offline'
// v3: collections/collection-info/saved-searches now stored in OpenAPI JSON
// wire-shape (Sets→arrays). Pre-v3 entries were JSON.stringify'd runtime objects
// where Sets serialized to "{}" — re-hydration with *FromJSON throws. Drop those.
const DB_VERSION = 3

const STORES = {
  USER_INFO: 'user-info',
  COLLECTIONS: 'collections',
  COLLECTION_INFO: 'collection-info',
  SAVED_SEARCHES: 'saved-searches',
} as const

export interface Cached<T> {
  data: T
  cachedAt: number
}

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = (event) => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORES.USER_INFO)) {
        db.createObjectStore(STORES.USER_INFO)
      }
      if (!db.objectStoreNames.contains(STORES.COLLECTIONS)) {
        db.createObjectStore(STORES.COLLECTIONS)
      }
      if (!db.objectStoreNames.contains(STORES.COLLECTION_INFO)) {
        db.createObjectStore(STORES.COLLECTION_INFO)
      }
      if (!db.objectStoreNames.contains(STORES.SAVED_SEARCHES)) {
        db.createObjectStore(STORES.SAVED_SEARCHES)
      }
      const tx = request.transaction
      if (event.oldVersion < 3 && tx) {
        tx.objectStore(STORES.COLLECTIONS).clear()
        tx.objectStore(STORES.COLLECTION_INFO).clear()
        tx.objectStore(STORES.SAVED_SEARCHES).clear()
      }
    }

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function put<T>(storeName: string, key: string, value: Cached<T>): Promise<void> {
  return new Promise((resolve, reject) => {
    openDB().then(db => {
      const tx = db.transaction(storeName, 'readwrite')
      const store = tx.objectStore(storeName)
      const req = store.put(value, key)
      req.onsuccess = () => resolve()
      req.onerror = () => reject(req.error)
    }).catch(reject)
  })
}

function get<T>(storeName: string, key: string): Promise<Cached<T> | null> {
  return new Promise((resolve, reject) => {
    openDB().then(db => {
      const tx = db.transaction(storeName, 'readonly')
      const store = tx.objectStore(storeName)
      const req = store.get(key)
      req.onsuccess = () => {
        resolve(req.result ?? null)
      }
      req.onerror = () => reject(req.error)
    }).catch(reject)
  })
}

function deleteByKey(storeName: string, key: string): Promise<void> {
  return new Promise((resolve, reject) => {
    openDB().then(db => {
      const tx = db.transaction(storeName, 'readwrite')
      const store = tx.objectStore(storeName)
      const req = store.delete(key)
      req.onsuccess = () => resolve()
      req.onerror = () => reject(req.error)
    }).catch(reject)
  })
}

function getAllKeys(storeName: string): Promise<string[]> {
  return new Promise((resolve, reject) => {
    openDB().then(db => {
      const tx = db.transaction(storeName, 'readonly')
      const store = tx.objectStore(storeName)
      const req = store.getAllKeys()
      req.onsuccess = () => resolve(req.result as string[])
      req.onerror = () => reject(req.error)
    }).catch(reject)
  })
}

function userKey(email: string, suffix: string): string {
  return `${email}:${suffix}`
}

export function saveUserInfo(email: string, user: UserInfoJson): Promise<void> {
  return put(STORES.USER_INFO, userKey(email, 'user-info'), {
    data: UserInfoJsonToJSON(user),
    cachedAt: Date.now(),
  })
}

// All save* functions store the OpenAPI JSON wire-shape (Sets→arrays, Dates→ISO
// strings) produced by the generated *ToJSON helpers. A naive structuredClone or
// JSON.parse(JSON.stringify(...)) silently drops Sets to "{}" and corrupts the cache
// — the offline middleware later JSON.stringify's this back into a Response body
// for the openapi client to parse with *FromJSON, so it must already be wire-shape.

export function saveCollections(email: string, collections: CollectionSummaryJson[]): Promise<void> {
  return put(STORES.COLLECTIONS, userKey(email, 'collections'), {
    data: collections.map(c => CollectionSummaryJsonToJSON(c)),
    cachedAt: Date.now(),
  })
}

export function saveCollectionInfo(email: string, info: CollectionInfoJson): Promise<void> {
  return put(STORES.COLLECTION_INFO, userKey(email, `collection-info:${info.id}`), {
    data: CollectionInfoJsonToJSON(info),
    cachedAt: Date.now(),
  })
}

export function saveSavedSearches(
  email: string,
  collectionId: string,
  savedSearches: SavedSearchJson[],
): Promise<void> {
  return put(STORES.SAVED_SEARCHES, userKey(email, `saved-searches:${collectionId}`), {
    data: savedSearches.map(s => SavedSearchJsonToJSON(s)),
    cachedAt: Date.now(),
  })
}

export async function loadUserInfo(): Promise<{ email: string; data: UserInfoJson; cachedAt: number } | null> {
  const allKeys = await getAllKeys(STORES.USER_INFO)
  const userInfoKey = allKeys.find(k => k.endsWith(':user-info'))
  if (!userInfoKey) return null

  const cached = await get<Record<string, unknown>>(STORES.USER_INFO, userInfoKey)
  if (!cached) return null

  const email = userInfoKey.replace(':user-info', '')
  return { email, data: UserInfoJsonFromJSON(cached.data), cachedAt: cached.cachedAt }
}

export async function loadCollections(email: string): Promise<CollectionSummaryJson[] | null> {
  const cached = await get<CollectionSummaryJson[]>(STORES.COLLECTIONS, userKey(email, 'collections'))
  return cached?.data ?? null
}

export async function loadCollectionInfo(email: string, collectionId: string): Promise<CollectionInfoJson | null> {
  const cached = await get<CollectionInfoJson>(STORES.COLLECTION_INFO, userKey(email, `collection-info:${collectionId}`))
  return cached?.data ?? null
}

export async function loadSavedSearches(
  email: string,
  collectionId: string,
): Promise<SavedSearchJson[] | null> {
  const cached = await get<SavedSearchJson[]>(
    STORES.SAVED_SEARCHES,
    userKey(email, `saved-searches:${collectionId}`),
  )
  return cached?.data ?? null
}

export async function purgeForUser(email: string): Promise<void> {
  for (const storeName of Object.values(STORES)) {
    const keys = await getAllKeys(storeName)
    for (const key of keys) {
      if (key.startsWith(`${email}:`)) {
        await deleteByKey(storeName, key)
      }
    }
  }
}

export async function purgeAll(): Promise<void> {
  const db = await openDB()
  for (const storeName of Object.values(STORES)) {
    db.transaction(storeName, 'readwrite').objectStore(storeName).clear()
  }
}

export async function getLastSyncedAt(email: string): Promise<number | null> {
  const cached = await get<CollectionSummaryJson[]>(STORES.COLLECTIONS, userKey(email, 'collections'))
  return cached?.cachedAt ?? null
}
