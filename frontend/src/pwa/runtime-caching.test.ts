import { describe, expect, it } from 'vitest'
import { isSameOriginAsset } from './runtime-caching'

const match = (origin: string, pathname: string, sameOrigin: boolean) =>
  isSameOriginAsset({ url: new URL(origin + pathname), sameOrigin })

describe('isSameOriginAsset', () => {
  // Positive: same-origin assets the SW should cache
  it.each([
    ['/assets/app.js', '.js'],
    ['/assets/style.css', '.css'],
    ['/logo.png', '.png'],
    ['/icon.svg', '.svg'],
    ['/fonts/bar.woff2', '.woff2'],
  ])('matches same-origin %s', (pathname) => {
    expect(match('https://linkweave.dev', pathname, true)).toBe(true)
  })

  // Negative: cross-origin subresources must bypass the SW. These are the
  // exact URLs BookmarkFavicon.vue emits for hosts on a collection's favicon
  // allowlist — they previously matched the SW's runtime-caching regex and
  // failed with NS_ERROR_INTERCEPTION_FAILED in Firefox when the upstream
  // fetch rejected inside CacheFirst.
  it.each([
    ['https://gitlab.dvbern.ch', '/favicon.ico'],
    ['https://dev-vacme-zh.apps.mercury.ocp.dvbern.ch', '/favicon.ico'],
    ['https://argocd-server-vacme-vacme-default-dev.apps.mercury.ocp.dvbern.ch', '/favicon.ico'],
    ['https://example.invalid', '/favicon.ico'],
    ['https://cdn.example.com', '/lib.js'],
    ['https://fonts.gstatic.com', '/font.woff2'],
  ])('does NOT match cross-origin %s%s', (origin, pathname) => {
    expect(match(origin, pathname, false)).toBe(false)
  })

  // Negative: .ico is no longer cached at all — the app has no same-origin
  // .ico, and the only .ico requests it issues are cross-origin favicons.
  it('does NOT match same-origin .ico (extension dropped)', () => {
    expect(match('https://linkweave.dev', '/favicon.ico', true)).toBe(false)
  })

  // Negative: non-asset paths on the same origin
  it.each([
    ['/api/collections/x/bookmarks/y/favicon'],
    ['/collections/abc-123'],
  ])('does NOT match same-origin non-asset path %s', (pathname) => {
    expect(match('https://linkweave.dev', pathname, true)).toBe(false)
  })
})
