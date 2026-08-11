import { Configuration } from '@/api/generated'
import { CLIENT_ID_HEADER, clientId } from '@/lib/client-id'
import { createOfflineMiddleware } from '@/lib/offline-middleware'
import { createLocaleMiddleware } from '@/lib/locale-middleware'

const config = new Configuration({
  basePath: '',
  credentials: 'include',
  // Marks requests as AJAX so Quarkus OIDC returns 499 instead of a 302 to the
  // IDP when the session is invalid (see quarkus.oidc.authentication.java-script-auto-redirect).
  // X-Client-Id names the originating tab so the live-update channel can skip
  // notifying it about its own writes (UC-104 BR-205); the server treats it as
  // opaque and never authenticates anything with it.
  headers: { 'X-Requested-With': 'XMLHttpRequest', [CLIENT_ID_HEADER]: clientId },
  middleware: [createLocaleMiddleware(), createOfflineMiddleware()],
})

export { config }
