import { Configuration } from '@/api/generated'
import { createOfflineMiddleware } from '@/lib/offline-middleware'

const config = new Configuration({
  basePath: '',
  credentials: 'include',
  // Marks requests as AJAX so Quarkus OIDC returns 499 instead of a 302 to the
  // IDP when the session is invalid (see quarkus.oidc.authentication.java-script-auto-redirect).
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
  middleware: [createOfflineMiddleware()],
})

export { config }
