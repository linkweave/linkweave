import {
  AuthResourceApi,
  BookmarkResourceApi,
  CollectionResourceApi,
  Configuration,
  FolderResourceApi,
  TagResourceApi,
} from './api'
import type { EffectiveConfig } from './config'
import { CliError, NOT_AUTHENTICATED_MESSAGE } from './errors'

export interface ApiClients {
  auth: AuthResourceApi
  bookmarks: BookmarkResourceApi
  collections: CollectionResourceApi
  folders: FolderResourceApi
  tags: TagResourceApi
}

/** Builds authenticated API clients. Generated paths already include `/api`. */
export function createClients(server: string, apiKey: string): ApiClients {
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
  }
}

/** Like createClients, but fails per UC-079 A1 when no key is configured. */
export function createAuthenticatedClients(config: EffectiveConfig): ApiClients {
  if (!config.apiKey) throw new CliError(NOT_AUTHENTICATED_MESSAGE)
  return createClients(config.server, config.apiKey)
}
