import { type APIRequestContext } from '@playwright/test'

/** Base path for the REST API used from e2e specs. */
export const BASE = '/api'

/** A REST resource that returns its generated id on creation. */
export type Created = { id: string }

/**
 * Generic REST helper for seeding e2e state over HTTP.
 *
 * Retries transient 5xx — the dev SQLite DB occasionally returns 500 under
 * concurrent writes — and fails fast on 4xx so real bugs surface. Uses 5
 * attempts with exponential backoff over the 4 gaps between them
 * (200/400/800/1600 ms, plus up to 100 ms jitter each → ~3–3.4 s of waiting)
 * so the helper survives the sustained contention peaks a 3-worker e2e run
 * produces.
 */
export async function api<T>(
  request: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  body?: unknown,
): Promise<T> {
  const opts: Parameters<APIRequestContext['post']>[1] = body === undefined ? {} : { data: body }
  let lastStatus = 0
  let lastBody = ''
  const attempts = 5
  for (let attempt = 0; attempt < attempts; attempt++) {
    const resp =
      method === 'GET'
        ? await request.get(path, opts)
        : method === 'PUT'
          ? await request.put(path, opts)
          : await request.post(path, opts)
    lastStatus = resp.status()
    if (resp.ok()) {
      // Some endpoints (notably PUT) answer with an empty body — treat as void.
      const text = await resp.text()
      return (text ? JSON.parse(text) : undefined) as T
    }
    lastBody = await resp.text().catch(() => '')
    if (lastStatus < 500) break
    // No point sleeping after the last attempt — nothing follows it but the throw.
    if (attempt === attempts - 1) break
    console.warn(
      `[e2e] ${method} ${path} → ${lastStatus}, retrying (attempt ${attempt + 1}/${attempts})`,
    )
    // Exponential backoff: 200ms, 400ms, 800ms, 1600ms (with jitter).
    await new Promise((r) => setTimeout(r, 200 * Math.pow(2, attempt) + Math.random() * 100))
  }
  throw new Error(`${method} ${path} failed: ${lastStatus} ${lastBody}`)
}

/** Creates a collection via the REST API and returns its id. */
export async function createCollectionViaApi(
  request: APIRequestContext,
  name: string,
): Promise<string> {
  const { id } = await api<Created>(request, 'POST', `${BASE}/collections`, { name })
  return id
}

/** Creates a bookmark via the REST API and returns its id. */
export async function createBookmarkViaApi(
  request: APIRequestContext,
  collectionId: string,
  title: string,
  url: string,
): Promise<string> {
  const { id } = await api<Created>(request, 'POST', `${BASE}/bookmarks`, { collectionId, title, url })
  return id
}
