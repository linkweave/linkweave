import { Configuration } from '@/api/generated'

/** Keys stored in chrome.storage.sync by the options page. */
export interface ExtensionConfig {
  apiUrl: string
  webAppUrl: string
}

const DEFAULT_API_URL = import.meta.env.VITE_CHAINLINK_API_URL as string
const DEFAULT_WEB_APP_URL = import.meta.env.VITE_CHAINLINK_URL as string

/**
 * Reads user-configured URLs from chrome.storage.sync, falling back to
 * the build-time env vars if nothing has been set.
 */
export async function loadExtensionConfig(): Promise<ExtensionConfig> {
  const stored = await chrome.storage.sync.get({
    apiUrl: DEFAULT_API_URL,
    webAppUrl: DEFAULT_WEB_APP_URL,
  })
  return {
    apiUrl: (stored['apiUrl'] as string) || DEFAULT_API_URL,
    webAppUrl: (stored['webAppUrl'] as string) || DEFAULT_WEB_APP_URL,
  }
}

/** Save user-configured URLs to chrome.storage.sync. */
export async function saveExtensionConfig(config: ExtensionConfig): Promise<void> {
  await chrome.storage.sync.set({
    apiUrl: config.apiUrl,
    webAppUrl: config.webAppUrl,
  })
}

/** Returns the default (build-time) config values. */
export function getDefaults(): ExtensionConfig {
  return { apiUrl: DEFAULT_API_URL, webAppUrl: DEFAULT_WEB_APP_URL }
}

/** Creates an API Configuration from a resolved config. */
export function createApiConfig(config: ExtensionConfig): Configuration {
  return new Configuration({
    basePath: config.apiUrl,
    credentials: 'include',
  })
}
