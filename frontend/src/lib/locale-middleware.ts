import type { Middleware } from '@/api/generated/runtime'
import i18n from '@/i18n'

/**
 * Sends the active in-app locale to the backend via the Accept-Language header.
 *
 * Without this, the backend's HttpAcceptLanguageRequestFilter sees only the
 * browser/OS Accept-Language, so a user who switches the UI language in-app
 * (UserMenuLw.vue) would still get server-rendered messages — AppException
 * translations surfaced by error-utils.ts — in the browser's language.
 *
 * The bare locale code (en/de/fr) is matched as a language range against the
 * backend's AppLanguages (en-US/de-CH/fr-CH), so no region suffix is needed.
 * Read lazily on each request so live language switches take effect immediately.
 */
export function createLocaleMiddleware(): Middleware {
  return {
    pre: async (context) => ({
      url: context.url,
      init: {
        ...context.init,
        headers: {
          ...context.init.headers,
          'Accept-Language': i18n.global.locale.value,
        },
      },
    }),
  }
}
