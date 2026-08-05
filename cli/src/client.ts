import {
  AuthResourceApi,
  BookmarkResourceApi,
  CollectionResourceApi,
  Configuration,
  FolderResourceApi,
  TagResourceApi,
  TrashbinResourceApi,
} from './api'
import type { EffectiveConfig } from './config'
import { CliError, NOT_AUTHENTICATED_MESSAGE } from './errors'

export interface ApiClients {
  auth: AuthResourceApi
  bookmarks: BookmarkResourceApi
  collections: CollectionResourceApi
  folders: FolderResourceApi
  tags: TagResourceApi
  trash: TrashbinResourceApi
}

let tlsWarned = false

/**
 * Turns off certificate verification for this process (UC-079 A7).
 *
 * Applied here rather than in option parsing because the decision can come
 * from the stored config as well as from `--insecure`, and the stored value is
 * only known once the effective config has been resolved. Node reads this
 * variable per request, so setting it before the first call is enough.
 *
 * It warns every time, including when the setting came from `login --insecure`
 * rather than this invocation: running without certificate verification is a
 * state worth being reminded of, not a preference to forget about. Completion
 * never shows it — the generated scripts discard stderr.
 */
function applyTlsPolicy(insecure: boolean): void {
  if (!insecure) return
  process.env['NODE_TLS_REJECT_UNAUTHORIZED'] = '0'
  if (tlsWarned) return
  tlsWarned = true
  process.stderr.write('⚠ TLS verification disabled. Only use this with trusted servers.\n')
}

/** Builds authenticated API clients. Generated paths already include `/api`. */
export function createClients(server: string, apiKey: string, insecure = false): ApiClients {
  applyTlsPolicy(insecure)
  const configuration = new Configuration({
    basePath: server,
    headers: { 'X-API-Key': apiKey },
  })
  return {
    auth: new AuthResourceApi(configuration),
    bookmarks: new BookmarkResourceApi(configuration),
    collections: new CollectionResourceApi(configuration),
    folders: new FolderResourceApi(configuration),
    tags: new TagResourceApi(configuration),
    trash: new TrashbinResourceApi(configuration),
  }
}

/** Like createClients, but fails per UC-079 A1 when no key is configured. */
export function createAuthenticatedClients(config: EffectiveConfig): ApiClients {
  if (!config.apiKey) throw new CliError(NOT_AUTHENTICATED_MESSAGE)
  return createClients(config.server, config.apiKey, config.insecure === true)
}
