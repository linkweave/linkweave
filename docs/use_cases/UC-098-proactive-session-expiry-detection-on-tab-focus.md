# Use Case: Proactive Session Expiry Detection on Tab Focus

## Overview

**Use Case ID:** UC-098
**Use Case Name:** Proactive Session Expiry Detection on Tab Focus
**Primary Actor:** User
**Goal:** Detect an expired session the moment the user returns to a backgrounded tab, and send them to the login page before their first action fails — without ever mistaking a network outage for an expired session
**Status:** Implemented

**Implementation Notes:**
- `src/lib/session-watch.ts` — `installSessionExpiryWatch()`: visibility listener, 60s-throttled probe of `/api/auth/me` (raw fetch with `X-Requested-With` so an expired session yields 499, not a 302); only 401/499 count as expiry (BR-098-1), unit-tested in `session-watch.spec.ts`
- Wired in `MainLayout.vue` `onMounted` (authenticated views only, A4)
- Expiry routes through `auth.handleSessionExpired()` — the same handler the reactive 401/499 middleware uses (BR-098-3); shows the `login.sessionExpired` toast, captures the return target (UC-099), logs out
- A2 (re-validate on reconnect) — NOT implemented; the next failing request catches it reactively

**Context:** Today, session expiry is only detected *reactively*: a request must fail with HTTP 401/499 before the user is logged out and redirected (see UC-030). The common real-world case is a tab left open overnight — the user returns, starts typing into a form, and only discovers the dead session when their first save fails. This use case closes that gap by re-validating the session when the tab regains visibility.

**Related:** UC-033 (Login with Google), UC-048 (Browse Bookmarks Offline), UC-049 (Resume Online Session), UC-099 (Return to Previous Page After Re-Login)

## Preconditions

- User is authenticated and has the app open on a non-public route
- The browser tab has been hidden (backgrounded, minimized, or the device was asleep) and becomes visible again
- The user's server-side OIDC session has expired while the tab was hidden

## Main Success Scenario

1. The user returns to the backgrounded tab (the browser fires a visibility-change event, the tab becomes visible).
2. System determines that a session re-validation is due (see BR-098-2 throttling).
3. System silently sends a lightweight session-validation request to the server.
4. Server responds with HTTP 401 or 499, indicating the session is no longer authenticated.
5. System invokes the single shared session-expired pathway (the same one used for reactive detection): all client stores are reset and the current user's offline cache is purged.
6. System displays a notification: "Your session has expired. Please sign in again."
7. Router redirects the user to the login page, preserving the route they were on for post-login return (UC-099).
8. User re-authenticates and continues working — no action of theirs ever failed.

## Alternative Flows

### A1: Session Still Valid

**Trigger:** Server responds with HTTP 200 to the validation request (step 4)
**Flow:**

1. System takes no visible action; the user continues working uninterrupted.
2. The validation timestamp is updated for throttling purposes (BR-098-2).

### A2: Validation Request Fails Due to Network / Server Unavailability

**Trigger:** The validation request fails with a network error, or the server responds with 5xx / gateway errors (step 4)
**Flow:**

1. System does **not** treat this as session expiry: no logout, no store reset, no cache purge, no redirect.
2. The existing offline handling takes over (offline banner, cached data — UC-048/UC-049).
3. System re-validates the session once connectivity is restored (hooks into the "back online" flow of UC-049).

### A3: Tab Regains Focus in Rapid Succession

**Trigger:** The tab flips between hidden and visible several times within the throttle window (step 2)
**Flow:**

1. System skips the validation request because the last check is more recent than the throttle interval (BR-098-2).
2. Use case ends without any server traffic.

### A4: User Is on a Public Route or Not Authenticated

**Trigger:** The tab becomes visible while the user is on a public route (login, register, privacy) or no user is signed in (step 1)
**Flow:**

1. System skips the validation entirely — there is no session to protect.
2. Use case ends.

## Postconditions

### Success Postconditions

- The expired session is discovered before the user attempts any action
- All client-side stores are reset and the current user's offline cache is purged (user-scoped, per BR-049-4)
- The user is on the login page with a clear explanation of why
- The previously active route has been captured for post-login return (UC-099)

### Failure Postconditions

- If the check could not run (offline, server down), the user's client-side state is fully intact: no logout, no cache purge, and offline mode behaves exactly as in UC-048
- Session expiry will still be caught reactively by the existing 401/499 handling on the next request

## Business Rules

### BR-098-1: Only 401/499 Mean "Expired"

Only an explicit HTTP 401 or 499 response from the server counts as an expired session. Network errors, timeouts, and 5xx responses MUST NOT trigger logout or cache purge — otherwise a merely-offline user would be logged out and lose their offline cache, defeating UC-048. This distinction is the critical safety property of this use case.

### BR-098-2: Throttled Validation

Session validation on tab focus runs at most once per throttle interval (proposed: 60 seconds), and only when the tab was actually hidden beforehand. This prevents request storms from rapid tab switching while still catching the overnight-tab case, where the hidden period vastly exceeds the interval.

### BR-098-3: Single Session-Expired Pathway

Proactive detection MUST route through the same session-expired handler as reactive detection (the middleware's 401/499 handling), so that re-entrancy guarding, store reset, user-scoped cache purge, and redirect behavior stay identical regardless of how the expiry was discovered.

### BR-098-4: Silent When Healthy

A validation that confirms the session is still valid produces no visible UI change. The user must never notice the check happening.
