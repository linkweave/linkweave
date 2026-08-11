import { ref } from 'vue'

/**
 * Cache-busting nonces for bookmark screenshot URLs, keyed by bookmark id.
 *
 * A bookmark carries no screenshot state on the wire — the preview is a plain
 * GET on the screenshot endpoint that answers 204 until a capture exists — so
 * "this bookmark now has a preview" cannot be expressed by refetching the
 * collection. The only way to make the browser look again is to change the URL,
 * which is what this nonce does.
 *
 * Deliberately shared state rather than a ref per card: a manual refresh
 * (UC-093) and a live SCREENSHOT_READY notification (UC-104 A1) are the same
 * event as far as the image is concerned, and giving them one mechanism keeps a
 * card from showing a stale capture just because the reload came from the
 * server rather than from its own menu.
 */
const nonces = ref<Record<string, number>>({})

/** `undefined` until something has invalidated this bookmark's preview. */
export function previewNonceOf(bookmarkId: string): number | undefined {
  return nonces.value[bookmarkId]
}

export function bumpPreviewNonce(bookmarkId: string): void {
  nonces.value = { ...nonces.value, [bookmarkId]: Date.now() }
}

/**
 * Drops every nonce. Called on collection switch: the entries are only useful
 * while their cards are on screen, and without this the map would grow for the
 * lifetime of the tab.
 */
export function clearPreviewNonces(): void {
  nonces.value = {}
}
