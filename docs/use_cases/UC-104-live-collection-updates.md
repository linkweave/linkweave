# Use Case: Receive Live Collection Updates

## Overview

**Use Case ID:** UC-104
**Use Case Name:** Receive Live Collection Updates
**Primary Actor:** User
**Goal:** See a collection's content update on its own — when deferred server-side work on its bookmarks completes, and when another member of a shared collection changes something — without manually reloading the page or hunting for a refresh button.
**Status:** Draft

**Context:** When a collection is shared (UC-023), a member's additions are invisible to everyone else until they happen to reload — two people working the same collection cannot see each other's work. The same gap appears without any second user: screenshot capture (UC-054) is performed by a scheduled batch job *after* the save returns, so a freshly saved bookmark shows no preview until a reload. In both cases the server knows something changed and has no way to say so.

**Explicitly not in scope: LLM auto-tagging (UC-097).** Tag suggestions are computed synchronously, before the bookmark is saved, and are never persisted (BR-078) — there is no deferred server-side completion to announce, so auto-tagging neither needs nor triggers a live update.

This use case introduces **one** per-collection notification channel serving deferred server-side completions and collaborator changes alike. The channel carries *notifications*, not data — see BR-202.

**Design document:** [plans/live-collection-updates.md](../plans/live-collection-updates.md)

**Related:** UC-023 (Share Collection), UC-025 (Revoke Collection Access), UC-054 (View Bookmark Screenshot Previews), UC-031 (Import Browser Bookmarks), UC-096 (Review and Select Bookmarks Before Import), UC-048 (Browse Bookmarks Offline), UC-049 (Resume Online Session), UC-098 (Proactive Session Expiry Detection on Tab Focus) — shares the tab-focus recovery path used as this feature's fallback.

---

## Preconditions

- The user is authenticated.
- The user has access to the collection they are viewing.
- The client is online (see A6 for the offline case).

## Main Success Scenario

1. User opens a collection that is shared with other members.
2. System displays the collection's bookmarks and begins listening for updates to that collection.
3. Another member with access to the same collection adds a bookmark to it.
4. System stores the new bookmark and, once the change is durable, notifies every client currently listening to that collection that the collection gained a bookmark (BR-203).
5. User's client reloads the affected collection state through the normal read path (BR-202).
6. User's view updates in place — the new bookmark appears, respecting the viewer's current sort, filter, and search selection; one that does not match the active filter does not appear.
7. System shows a brief, non-blocking indication that new content arrived, attributed to the member who added it (BR-209).

## Alternative Flows

### A1: Deferred Screenshot Capture Completes

**Trigger:** The scheduled capture job stores a preview image for a bookmark in the collection (step 4).
**Flow:**

1. System notifies listening clients that the bookmark changed.
2. Each client reloads and the preview image appears in place, without the user reloading or navigating.
3. No attribution is shown — the change originated from a background job, not a person.
4. Use case continues at step 7.

### A2: The Acting User Is Also Listening

**Trigger:** The user who caused the change is viewing the same collection in the same tab (step 4).
**Flow:**

1. System does not apply a redundant update for that user — their own action already updated their view.
2. No notification indicator is shown to them for their own change (BR-205).
3. Use case continues at step 7 for all other listeners.

### A3: Connection Drops

**Trigger:** The listening connection is interrupted — network loss, server restart, proxy timeout (step 2 or later).
**Flow:**

1. Client stops receiving updates and reconnects after a short, increasing delay.
2. On successful reconnection, the client reloads the collection's current state in full, because changes during the gap were not delivered (BR-204).
3. Use case continues at step 2.

### A4: Connection Cannot Be Established

**Trigger:** The listening connection fails repeatedly, or the environment does not support it (step 2).
**Flow:**

1. System stops attempting to reconnect after a bounded number of tries.
2. The collection remains fully usable; the view refreshes when the user returns to the tab or navigates.
3. No error is presented — live updating is an enhancement, not a requirement (BR-206).

### A5: Access Revoked While Listening

**Trigger:** The user's access to the collection is withdrawn (UC-025) while their connection is open (step 4).
**Flow:**

1. System stops sending that user updates for the collection and closes their connection.
2. The user's next action against the collection is denied by the normal authorization path.
3. Use case ends.

### A6: User Is Offline

**Trigger:** The client has no network connection (step 2).
**Flow:**

1. System does not attempt to listen for updates.
2. The user browses the cached collection per UC-048.
3. When connectivity returns, the client resumes per UC-049 and re-establishes listening.
4. Use case continues at step 2.

### A7: Session Expires While Listening

**Trigger:** The user's session expires during a long-open connection (step 2 or later).
**Flow:**

1. System closes the connection.
2. The client's reconnection attempt is rejected as unauthenticated.
3. Client routes the user through the existing session-expiry handling (UC-098/UC-099) rather than reconnecting.
4. Use case ends.

### A8: User Switches Collections

**Trigger:** User navigates from one collection to another (step 6).
**Flow:**

1. System stops listening for updates to the previous collection.
2. System begins listening for updates to the newly opened collection (BR-207).
3. Use case continues at step 2.

### A9: Deferred Capture Fails

**Trigger:** The deferred work fails — screenshot service unreachable, capture rejected by fetch policy (A1, step 1).
**Flow:**

1. System records the failure and notifies listening clients that the action concluded unsuccessfully.
2. Client clears any in-progress indicator for that item and surfaces the failure quietly.
3. Use case continues at step 7.

## Postconditions

### Success Postconditions

- The user's view of the collection reflects the change without a manual reload.
- Every other client viewing the same collection, and only those with access to it, is likewise updated.
- No notification is delivered to a user who lacks access to the collection.

### Failure Postconditions

- The collection view remains correct but static until the user refreshes, returns to the tab, or navigates.
- No error blocks or interrupts the user's work.
- The underlying change is still persisted — a failure to notify never means a failure to save.

## Business Rules

### BR-201: Access Checked at Subscribe and Enforced on Revocation

A client may only listen to a collection it currently has access to. Access is verified when the connection is established, using the same authorization path as every other collection operation. If access is later revoked, the connection is closed rather than left open (A5).

### BR-202: Notifications Carry No Authoritative State

An update notification identifies *what changed* (collection, item, kind of change, who caused it) — it is not the source of truth for the changed data. The client reloads the affected state through the normal read path. This keeps a single authoritative representation of a collection and prevents a client's view from diverging when a notification is missed, reordered, or delivered out of date.

### BR-203: Notify Only After the Change Is Durable

A notification is emitted only once the change it describes has been committed. A client that reacts instantly must never be able to read a state older than the change it was told about.

### BR-204: No Replay, No Backlog

Notifications are delivered only to clients connected at the moment of the change. There is no queue, no retained history, and no replay after reconnection. Missed changes are recovered by reloading the collection on reconnect (A3), on tab focus, or on navigation — never by replaying a log.

### BR-205: The Actor Is Not Notified of Their Own Change

The client that caused a change does not act on the resulting notification. Their view was already updated by their own request; re-applying it would cause visible churn and duplicate indicators.

### BR-206: Live Updating Is an Enhancement, Never a Dependency

No feature may require the update channel to function. If it cannot be established, drops, or is disabled, every use case it touches must continue to work through ordinary request/response. The fallback is the existing behaviour: refresh on navigation or on tab focus.

### BR-207: One Stream Per Collection Per Client

A client listens to at most one collection at a time — the one it is displaying. Opening a different collection replaces the previous subscription rather than accumulating connections.

### BR-208: Idle Connections Are Kept Alive and Reconnects Are Bounded

An open connection with no activity is kept alive by a periodic heartbeat so that intermediaries do not silently discard it. Reconnection uses an increasing delay and stops after a bounded number of attempts (A4). Establishing or maintaining the connection is exempt from the per-endpoint API rate limits that apply to ordinary requests, so that a reconnect loop cannot lock a user out of the API.

### BR-209: Updates Are Ambient, Not Interruptive

An update never opens a dialog, steals focus, moves the user's scroll position, or alters the item the user is currently editing. Changes are applied in place and signalled with a subtle indicator. A change to a bookmark that the user has open in an editor is deferred until the editor closes.

### BR-210: Notification Scope Is the Collection

Notifications are scoped to a single collection. A user with access to several collections receives updates only for the one they are viewing; the channel never carries information about collections the user does not currently have open.

## Notes / Future Considerations

### What this channel does and does not serve

An earlier draft justified this channel with three asynchronous flows. Two of them do not survive inspection, and are recorded here so they are not re-explored:

- **LLM auto-tagging is synchronous and pre-save.** Suggestions are requested from the form, returned in the response, and never persisted (BR-078). No deferred completion exists to announce.
- **Import commits synchronously.** The request returns after the write; the client already has the result.

That leaves two: **deferred screenshot capture** (A1) and **a collaborator's change** (the main scenario). Both are real; they differ in what the missing update costs — a cosmetic thumbnail in the first case, another person's work in the second.

### Delivery order

Design document: [plans/live-collection-updates.md](../plans/live-collection-updates.md).

1. **Channel + deferred screenshot-capture completion (A1).** Screenshot capture is the only asynchronous producer that exists today (`ScreenshotCaptureJobService`), and it is verifiable in a single browser with no second user — so it proves the transport with the least moving parts.
2. **Bookmark added/changed by another member** — the main scenario, as an additional change kind on the same channel.

Stopping after step 1 must leave a coherent, shippable feature.

### Considered: WebSocket (rejected for now)

Bidirectional transport is not needed — every client-to-server action already has a REST endpoint, and traffic is strictly server-to-client. A one-way stream reuses the existing cookie authentication, survives proxies as ordinary HTTP, and reconnects on its own. If presence or collaborative editing ever enters scope, revisit.

### Considered: Polling (rejected as the primary mechanism)

Polling every collection view on a timer costs a request per client per interval against a single-writer SQLite database, and still delivers job completion late. It remains the *fallback* (BR-206), not the mechanism.

### Deployment constraint

Notification fan-out is in-process. This is sound only while the application runs as a single instance — which the SQLite single-writer model already requires (see UC-095). Should the datastore ever move to a clustered engine, this channel needs a shared distribution mechanism, and that migration should be treated as part of the same effort.

### Out of scope

Presence indicators ("who else is here"), collaborative cursors, live editing conflict resolution, and cross-collection or account-level notification feeds. Also out of scope: notifying a user about a collection they do not currently have open (BR-210) — an inbox-style notification centre is a separate use case.
