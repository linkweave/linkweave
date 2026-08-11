import { ChangeKind, type CollectionEventJson } from '@/api/generated'
import { clientId } from '@/lib/client-id'
import { isOffline } from '@/lib/network-status'
import { bumpPreviewNonce, clearPreviewNonces } from '@/lib/preview-nonce'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Reconnect budget. Bounded and then silent: the channel is an enhancement,
 * never a dependency (BR-206, A4) — refetch on navigation and tab focus already
 * recover the same state, so a tab that cannot hold a stream keeps working
 * rather than nagging.
 */
const MAX_RECONNECT_ATTEMPTS = 6
const BASE_RECONNECT_DELAY_MS = 1_000
const MAX_RECONNECT_DELAY_MS = 30_000

/**
 * Window for folding a burst of notifications into one refetch. The capture job
 * commits a whole batch per tick, so several SCREENSHOT_READY events routinely
 * land within milliseconds of each other; without this each one would trigger
 * its own load of the same collection.
 */
const REFETCH_COALESCE_MS = 400

function reconnectDelay(attempt: number): number {
  const backoff = Math.min(BASE_RECONNECT_DELAY_MS * 2 ** attempt, MAX_RECONNECT_DELAY_MS)
  // Jittered so that a server restart does not bring every open tab back in the
  // same instant — the reconnect storm would then look exactly like the outage.
  return backoff / 2 + Math.random() * (backoff / 2)
}

/**
 * Live collection updates over SSE (UC-104).
 *
 * <p>Notifications say *what* changed and are never the source of truth: the
 * data is re-read through the ordinary paths (BR-202). Nothing here is required
 * for correctness — if the stream never opens, or drops and stays down, every
 * affected feature still works through request/response.
 */
export function useCollectionEvents(currentCollectionId: () => string | null) {
  const collectionStore = useCollectionStore()
  const notification = useNotificationStore()
  const { t } = useI18n()

  let source: EventSource | null = null
  let subscribedTo: string | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let refetchTimer: ReturnType<typeof setTimeout> | null = null
  let attempt = 0
  let everConnected = false

  function close() {
    source?.close()
    source = null
    subscribedTo = null
    everConnected = false
    attempt = 0
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (refetchTimer) clearTimeout(refetchTimer)
    reconnectTimer = null
    refetchTimer = null
  }

  /** Coalesced re-read through the normal path (BR-202). */
  function refetchSoon(collectionId: string) {
    if (refetchTimer) clearTimeout(refetchTimer)
    refetchTimer = setTimeout(() => {
      refetchTimer = null
      // Guard against the collection having been switched while we waited.
      if (currentCollectionId() !== collectionId) return
      void collectionStore.fetchCollectionInfo(collectionId, { silent: true })
    }, REFETCH_COALESCE_MS)
  }

  /**
   * Ambient, never interruptive (BR-209): a toast that names who changed
   * something, and nothing that moves focus, scroll, or an open editor. Skipped
   * without an actor — a background job is nobody, and "Something changed" is
   * not worth a toast when the change already applied itself (A1 step 3).
   */
  function announce(event: CollectionEventJson) {
    if (!event.actorName) return
    const message =
      event.kind === ChangeKind.BookmarkAdded
        ? t('liveUpdates.bookmarkAdded', { name: event.actorName })
        : event.kind === ChangeKind.BookmarkRemoved
          ? t('liveUpdates.bookmarkRemoved', { name: event.actorName })
          : t('liveUpdates.bookmarkChanged', { name: event.actorName })
    notification.info(message)
  }

  function handle(event: CollectionEventJson, collectionId: string) {
    switch (event.kind) {
      case ChangeKind.Heartbeat:
        // Carries no change — its only job was to keep the connection alive.
        return
      case ChangeKind.ScreenshotReady:
        // Two separate effects, because a capture changes two things: the image
        // (not in the collection JSON at all — only a URL change makes the
        // browser look again) and, when the bookmark had none, the description
        // backfilled from the captured page (which is in the JSON).
        if (event.bookmarkId) bumpPreviewNonce(event.bookmarkId)
        refetchSoon(collectionId)
        return
      case ChangeKind.BookmarkAdded:
      case ChangeKind.BookmarkChanged:
      case ChangeKind.BookmarkRemoved:
        // The notification says only *that* something changed; the list, its
        // sort, filter and search selection all come from re-reading (BR-202).
        refetchSoon(collectionId)
        announce(event)
        return
      default:
        // Unknown kind from a newer server: ignore rather than guess. A client
        // that hard-fails here would break on the first added change kind.
        return
    }
  }

  function connect(collectionId: string) {
    const url = `/api/collections/${encodeURIComponent(collectionId)}/events?clientId=${encodeURIComponent(clientId)}`
    const es = new EventSource(url)
    source = es
    subscribedTo = collectionId

    es.onopen = () => {
      const isReconnect = everConnected
      everConnected = true
      attempt = 0
      // Nothing is replayed for the gap we were away (BR-204), so the only safe
      // assumption after a reconnect is that we missed something.
      if (isReconnect) refetchSoon(collectionId)
    }

    es.onmessage = (message: MessageEvent<string>) => {
      let event: CollectionEventJson
      try {
        event = JSON.parse(message.data) as CollectionEventJson
      } catch {
        return // malformed frame: ignore rather than tear the stream down
      }
      handle(event, collectionId)
    }

    es.onerror = () => {
      // Take reconnection over from EventSource, whose built-in retry is a
      // fixed interval and never gives up — that turns a logged-out or
      // permanently failing tab into an endless request loop against the API.
      es.close()
      if (source !== es) return // already superseded by a newer subscription
      source = null
      if (attempt >= MAX_RECONNECT_ATTEMPTS) return // give up quietly (BR-206)
      const delay = reconnectDelay(attempt)
      attempt++
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        if (currentCollectionId() === collectionId && !isOffline.value) connect(collectionId)
      }, delay)
    }
  }

  function sync() {
    const collectionId = currentCollectionId()

    // Offline is not a failure to retry against: the browser has no route to
    // the server, and network-status owns detecting its return (A6). Dropping
    // the stream here also stops a reconnect loop from fighting that poller.
    if (!collectionId || isOffline.value) {
      close()
      return
    }
    if (subscribedTo === collectionId && source) return // already listening

    // One stream per client (BR-207): switching collections closes the old one
    // rather than accumulating subscriptions.
    close()
    clearPreviewNonces()
    connect(collectionId)
  }

  watch([currentCollectionId, isOffline], sync, { immediate: true })
  onUnmounted(close)
}
