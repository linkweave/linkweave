/**
 * Runtime-cache matcher for LinkWeave's Workbox service worker.
 *
 * Caches only same-origin static assets. Cross-origin subresource requests —
 * notably the <img> favicons emitted for hosts on a collection's favicon
 * allowlist — bypass the SW. Otherwise, when the upstream fetch fails inside
 * Workbox's CacheFirst strategy, the respondWith() promise rejects and Firefox
 * surfaces the failed interception to the page as NS_ERROR_INTERCEPTION_FAILED.
 *
 * Self-contained on purpose: vite-plugin-pwa serializes this function via
 * .toString() into the generated service worker, so it must not close over
 * any module-scope bindings.
 */
export const isSameOriginAsset = ({
  url,
  sameOrigin,
}: {
  url: URL
  sameOrigin: boolean
}): boolean =>
  sameOrigin && /\.(js|css|png|svg|woff2)$/.test(url.pathname)
