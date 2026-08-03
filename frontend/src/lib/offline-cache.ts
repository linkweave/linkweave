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

// One connection per session. Opening a fresh one per operation (and never
// closing it) piles up handles for the lifetime of the tab and blocks any
// future version upgrade, which waits for every open connection to go away.
let dbPromise: Promise<IDBDatabase> | null = null

function openDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise

  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
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

    request.onsuccess = () => {
      const db = request.result
      // Another tab upgrading the schema must not be blocked by this connection.
      db.onversionchange = () => {
        db.close()
        dbPromise = null
      }
      db.onclose = () => {
        dbPromise = null
      }
      resolve(db)
    }
    request.onerror = () => {
      dbPromise = null
      reject(request.error)
    }
  })

  return dbPromise
}

/**
 * Runs one request in its own transaction and settles only once that
 * transaction completes — resolving on the request's success alone would report
 * a write as done while it can still be rolled back by a failing transaction.
 */
async function runRequest<T>(
  storeName: string,
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest,
): Promise<T | undefined> {
  const db = await openDB()
  return new Promise<T | undefined>((resolve, reject) => {
    const tx = db.transaction(storeName, mode)
    const req = operation(tx.objectStore(storeName))
    let result: T | undefined
    req.onsuccess = () => {
      result = req.result as T
    }
    tx.oncomplete = () => resolve(result)
    tx.onerror = () => reject(tx.error ?? req.error)
    tx.onabort = () => reject(tx.error ?? req.error)
  })
}

async function put<T>(storeName: string, key: string, value: Cached<T>): Promise<void> {
  await runRequest(storeName, 'readwrite', store => store.put(value, key))
}

async function get<T>(storeName: string, key: string): Promise<Cached<T> | null> {
  const result = await runRequest<Cached<T>>(storeName, 'readonly', store => store.get(key))
  return result ?? null
}

async function deleteByKey(storeName: string, key: string): Promise<void> {
  await runRequest(storeName, 'readwrite', store => store.delete(key))
}

async function getAllKeys(storeName: string): Promise<string[]> {
  const keys = await runRequest<IDBValidKey[]>(storeName, 'readonly', store => store.getAllKeys())
  return (keys ?? []) as string[]
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

/**
 * With `expectedEmail` the cache of exactly that user is returned, never
 * another one's. Without it — the offline boot, where nobody is authenticated
 * yet — a cache holding more than one user is refused instead of handing back
 * whichever entry IndexedDB happens to yield first: on a shared browser that
 * would restore the previous user's identity (and their cached bookmarks) for
 * whoever opens the app next.
 */
export async function loadUserInfo(
  expectedEmail?: string,
): Promise<{ email: string; data: UserInfoJson; cachedAt: number } | null> {
  let email: string
  if (expectedEmail) {
    email = expectedEmail
  } else {
    const userInfoKeys = (await getAllKeys(STORES.USER_INFO)).filter(k => k.endsWith(':user-info'))
    if (userInfoKeys.length !== 1) return null
    email = userInfoKeys[0]!.replace(':user-info', '')
  }

  const cached = await get<Record<string, unknown>>(STORES.USER_INFO, userKey(email, 'user-info'))
  if (!cached) return null

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
  const storeNames = Object.values(STORES)
  // One transaction over all stores, awaited to completion — callers must be
  // able to rely on the data actually being gone when this resolves.
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(storeNames, 'readwrite')
    for (const storeName of storeNames) {
      tx.objectStore(storeName).clear()
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function getLastSyncedAt(email: string): Promise<number | null> {
  const cached = await get<CollectionSummaryJson[]>(STORES.COLLECTIONS, userKey(email, 'collections'))
  return cached?.cachedAt ?? null
}
