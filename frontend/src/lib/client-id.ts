/**
 * Identifies this **browser tab** for the lifetime of the page (UC-104 BR-205).
 *
 * Sent as `X-Client-Id` on mutating requests and as a query parameter when
 * subscribing to the live-update stream — `EventSource` cannot set headers, so
 * the stream has no other way to carry it. The server uses it for exactly one
 * thing: not sending a tab the notification for a change that tab just made.
 *
 * Held in memory on purpose. `localStorage` is shared by every tab of the same
 * origin, so a persisted id would make a user with the collection open twice
 * discard, in the second tab, precisely the updates it needs from the first —
 * a bug that is invisible until someone opens a second tab, and absent entirely
 * when testing with two different users.
 */
export const clientId = crypto.randomUUID()

export const CLIENT_ID_HEADER = 'X-Client-Id'
