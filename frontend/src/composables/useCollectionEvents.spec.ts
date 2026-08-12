// @vitest-environment happy-dom
import { ChangeKind, type CollectionEventJson } from '@/api/generated'
import { setBrowserOffline } from '@/lib/network-status'
import { previewNonceOf } from '@/lib/preview-nonce'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import i18n from '@/i18n'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, ref } from 'vue'
import { useCollectionEvents } from './useCollectionEvents'

// Mirrored from the composable (kept private there).
const REFETCH_COALESCE_MS = 400
const MAX_RECONNECT_ATTEMPTS = 6

/**
 * Stands in for the browser's EventSource, which happy-dom does not implement
 * and which could not be driven deterministically anyway. Every instance is
 * recorded so a test can assert what was opened, closed, and in what order.
 */
class FakeEventSource {
  static instances: FakeEventSource[] = []

  onopen: (() => void) | null = null
  onmessage: ((e: MessageEvent<string>) => void) | null = null
  onerror: (() => void) | null = null
  closed = false

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this)
  }

  close() {
    this.closed = true
  }

  /** Drives the stream as the server would. */
  open() {
    this.onopen?.()
  }

  emit(event: Partial<CollectionEventJson>) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(event) }))
  }

  fail() {
    this.onerror?.()
  }

  static get last(): FakeEventSource {
    const last = FakeEventSource.instances.at(-1)
    if (!last) throw new Error('no EventSource was opened')
    return last
  }

  static get live(): FakeEventSource[] {
    return FakeEventSource.instances.filter((es) => !es.closed)
  }
}

function screenshotReady(bookmarkId: string): Partial<CollectionEventJson> {
  return { collectionId: 'col-1', bookmarkId, kind: ChangeKind.ScreenshotReady }
}

/**
 * Mounts a throwaway component so `watch`/`onUnmounted` run with a real
 * instance. Every app is tracked and torn down after the test: `isOffline` is
 * module-level shared state, so a component left mounted keeps reacting to it
 * from inside later tests and opens streams they never asked for.
 */
const mounted: { unmount: () => void }[] = []

function mountWatching(collectionId: { value: string | null }) {
  const app = createApp({
    setup() {
      useCollectionEvents(() => collectionId.value)
      return () => null
    },
  })
  // The real i18n instance: the attribution text (BR-209) goes through it, and
  // a stub would let a missing translation key pass unnoticed.
  app.use(i18n)
  app.mount(document.createElement('div'))
  const handle = { unmount: () => app.unmount() }
  mounted.push(handle)
  return handle
}

describe('useCollectionEvents', () => {
  let fetchCollectionInfo: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.useFakeTimers()
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
    setActivePinia(createPinia())
    fetchCollectionInfo = vi.fn().mockResolvedValue(undefined)
    // Replace only the read path: the composable must go through the store
    // rather than applying event payloads itself (BR-202).
    useCollectionStore().fetchCollectionInfo = fetchCollectionInfo as never
  })

  afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    setBrowserOffline(false)
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('subscribes to the open collection and identifies the tab', () => {
    // ARRANGE / ACT
    mountWatching(ref('col-1'))

    // ASSERT
    expect(FakeEventSource.last.url).toMatch(/^\/api\/collections\/col-1\/events\?clientId=/)
  })

  it('ignores heartbeats', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()

    // ACT
    FakeEventSource.last.emit({ collectionId: 'col-1', kind: ChangeKind.Heartbeat })
    vi.advanceTimersByTime(REFETCH_COALESCE_MS * 2)

    // ASSERT — a keep-alive frame announces nothing
    expect(fetchCollectionInfo).not.toHaveBeenCalled()
  })

  it('invalidates the preview of the bookmark a capture completed for', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()

    // ACT
    FakeEventSource.last.emit(screenshotReady('bm-7'))

    // ASSERT — the screenshot URL must change, since a capture is invisible in
    // the collection JSON
    expect(previewNonceOf('bm-7')).toBeGreaterThan(0)
    expect(previewNonceOf('bm-other')).toBeUndefined()
  })

  it('re-reads through the store rather than applying the notification', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()

    // ACT
    FakeEventSource.last.emit(screenshotReady('bm-7'))
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — BR-202, and silently: the list is already on screen
    expect(fetchCollectionInfo).toHaveBeenCalledWith('col-1', { silent: true })
  })

  it('folds a burst of notifications into a single refetch', () => {
    // ARRANGE — one capture-job tick commits a whole batch
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()

    // ACT
    FakeEventSource.last.emit(screenshotReady('bm-1'))
    vi.advanceTimersByTime(REFETCH_COALESCE_MS / 4)
    FakeEventSource.last.emit(screenshotReady('bm-2'))
    vi.advanceTimersByTime(REFETCH_COALESCE_MS / 4)
    FakeEventSource.last.emit(screenshotReady('bm-3'))
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — one load of the collection, but every preview invalidated
    expect(fetchCollectionInfo).toHaveBeenCalledTimes(1)
    expect(previewNonceOf('bm-1')).toBeDefined()
    expect(previewNonceOf('bm-3')).toBeDefined()
  })

  it('reloads and names the member when someone else changes the collection', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    const info = vi.spyOn(useNotificationStore(), 'info')

    // ACT
    FakeEventSource.last.emit({
      collectionId: 'col-1',
      bookmarkId: 'bm-9',
      kind: ChangeKind.BookmarkAdded,
      actorName: 'Ada Lovelace',
    })
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — the list comes from re-reading (BR-202); the toast is the ambient
    // indicator (BR-209) and carries the attribution
    expect(fetchCollectionInfo).toHaveBeenCalledWith('col-1', { silent: true })
    expect(info).toHaveBeenCalledWith(expect.stringContaining('Ada Lovelace'))
  })

  it('does not claim a single bookmark when a batch changed many', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    const info = vi.spyOn(useNotificationStore(), 'info')

    // ACT — an import names no single bookmark
    FakeEventSource.last.emit({
      collectionId: 'col-1',
      kind: ChangeKind.BookmarkAdded,
      actorName: 'Ada Lovelace',
    })

    // ASSERT — "added a bookmark" would be a lie for a file of two hundred
    expect(info).toHaveBeenCalledWith('Ada Lovelace added bookmarks')
  })

  it('reloads and announces structural changes the same way as bookmark ones', () => {
    // ARRANGE — the sidebar, tag chips and property columns all arrive in the
    // same document as the bookmarks, so one re-read covers every kind
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    const info = vi.spyOn(useNotificationStore(), 'info')

    // ACT
    FakeEventSource.last.emit({
      collectionId: 'col-1',
      kind: ChangeKind.FolderRemoved,
      actorName: 'Ada Lovelace',
    })
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — a folder event names no folder, so it must not fall into the
    // bookmark plural wording
    expect(fetchCollectionInfo).toHaveBeenCalledWith('col-1', { silent: true })
    expect(info).toHaveBeenCalledWith('Ada Lovelace deleted a folder')
  })

  it('announces a tag or property change as a collection change', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    const info = vi.spyOn(useNotificationStore(), 'info')

    // ACT
    FakeEventSource.last.emit({
      collectionId: 'col-1',
      kind: ChangeKind.CollectionChanged,
      actorName: 'Ada Lovelace',
    })

    // ASSERT
    expect(info).toHaveBeenCalledWith('Ada Lovelace updated this collection')
  })

  it('says nothing when a change has no person behind it', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    const info = vi.spyOn(useNotificationStore(), 'info')

    // ACT — a capture job is nobody (A1 step 3)
    FakeEventSource.last.emit(screenshotReady('bm-7'))
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT
    expect(info).not.toHaveBeenCalled()
  })

  it('does not invalidate previews for a plain content change', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()

    // ACT — a batch edit names no single bookmark
    FakeEventSource.last.emit({
      collectionId: 'col-1',
      kind: ChangeKind.BookmarkChanged,
      actorName: 'Ada Lovelace',
    })
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — only a capture changes the image, so nothing may force every
    // screenshot on screen to be re-fetched
    expect(previewNonceOf('bm-1')).toBeUndefined()
    expect(fetchCollectionInfo).toHaveBeenCalledTimes(1)
  })

  it('holds exactly one stream when the collection changes', async () => {
    // ARRANGE
    const collectionId = ref<string | null>('col-1')
    mountWatching(collectionId)
    const first = FakeEventSource.last

    // ACT
    collectionId.value = 'col-2'
    await nextTick()

    // ASSERT — BR-207: one channel per client, re-targeted rather than added to
    expect(first.closed).toBe(true)
    expect(FakeEventSource.live).toHaveLength(1)
    expect(FakeEventSource.last.url).toContain('/collections/col-2/events')
  })

  it('closes the stream when the view goes away', () => {
    // ARRANGE
    const view = mountWatching(ref('col-1'))

    // ACT
    view.unmount()

    // ASSERT
    expect(FakeEventSource.live).toHaveLength(0)
  })

  it('does not open a stream while offline, and opens one when the network returns', async () => {
    // ARRANGE
    setBrowserOffline(true)
    mountWatching(ref('col-1'))
    expect(FakeEventSource.instances).toHaveLength(0)

    // ACT — A6: network-status owns detecting the return
    setBrowserOffline(false)
    await nextTick()

    // ASSERT
    expect(FakeEventSource.live).toHaveLength(1)
  })

  it('refetches after a reconnect, because the gap is never replayed', () => {
    // ARRANGE
    mountWatching(ref('col-1'))
    FakeEventSource.last.open()
    FakeEventSource.last.fail()
    vi.advanceTimersByTime(MAX_RECONNECT_DELAY_ENOUGH)

    // ACT — the retried connection succeeds
    FakeEventSource.last.open()
    vi.advanceTimersByTime(REFETCH_COALESCE_MS)

    // ASSERT — BR-204: whatever happened while we were away is only
    // recoverable by reloading
    expect(fetchCollectionInfo).toHaveBeenCalledWith('col-1', { silent: true })
  })

  it('gives up quietly after a bounded number of failed reconnects', () => {
    // ARRANGE
    mountWatching(ref('col-1'))

    // ACT — every attempt fails immediately
    for (let i = 0; i <= MAX_RECONNECT_ATTEMPTS + 2; i++) {
      FakeEventSource.last.fail()
      vi.advanceTimersByTime(MAX_RECONNECT_DELAY_ENOUGH)
    }

    // ASSERT — BR-206/A4: bounded, and never an error the user has to dismiss
    expect(FakeEventSource.instances.length).toBeLessThanOrEqual(MAX_RECONNECT_ATTEMPTS + 1)
    expect(FakeEventSource.live).toHaveLength(0)
  })
})

/** Longer than the capped backoff, so one advance always drains a pending retry. */
const MAX_RECONNECT_DELAY_ENOUGH = 31_000
