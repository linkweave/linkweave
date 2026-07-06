# Use Case: Return to Previous Page After Re-Login

## Overview

**Use Case ID:** UC-099
**Use Case Name:** Return to Previous Page After Re-Login
**Primary Actor:** User
**Goal:** After a forced re-login caused by session expiry, land back on the exact page (route, including collection and active filters encoded in the URL) the user was working on, instead of the default post-login landing page
**Status:** Implemented

**Implementation Notes:**
- `src/lib/postLoginRedirect.ts` — save/consume/clear of the return target in `sessionStorage` (survives the OIDC round trip, BR-099-2); internal-routes-only validation on both save and consume (BR-099-1), unit-tested in `postLoginRedirect.spec.ts`
- Captured in `auth.handleSessionExpired()` (expiry) and in the router guard's unauthenticated bounce (deep link / reload with dead session); a deliberate `logout()` clears it (A2)
- Consumed in `router/index.ts` `beforeEach` at the post-login landing (`home` or a public page while authenticated); unresolvable targets fall through to the default landing (A3) — `router.resolve().matched` guards unknown routes
- A1 (target no longer accessible) — relies on the target view's existing load-error handling; no dedicated toast

**Context:** Today, when the session expires the user is logged out and navigated to the login page without any memory of where they were. After signing back in, the default landing logic takes over (home → last/default collection, or manage-collections). A user who was deep in a specific collection with filters applied loses that place and must navigate back manually.

**Related:** UC-033 (Login with Google), UC-098 (Proactive Session Expiry Detection on Tab Focus), UC-002 (Auto-Navigate to Collection)

## Preconditions

- User was authenticated and on a non-public route (e.g. a collection view with active filters)
- The session expires and is detected — either reactively via a 401/499 response or proactively via UC-098
- The system redirects the user to the login page

## Main Success Scenario

1. System detects the expired session while the user is on a specific route (e.g. `/collections/{id}` with filter parameters).
2. System captures the current route — path and query string — as the return target.
3. Router redirects to the login page, carrying the return target as a `redirect` parameter.
4. User clicks "Sign in with Google" and completes the OIDC login flow (full-page round trip to the identity provider, UC-033).
5. System completes authentication and finds the preserved return target.
6. System validates the return target (see BR-099-1: internal app routes only).
7. Router navigates to the return target instead of the default landing page.
8. User continues working where they left off; filters and view state encoded in the URL are restored.

## Alternative Flows

### A1: Return Target No Longer Accessible

**Trigger:** Navigating to the return target fails because the resource was deleted or the user's access was revoked in the meantime, or the (possibly different) signed-in account never had access (step 7)
**Flow:**

1. The target view fails to load (HTTP 403/404 from the server).
2. System displays a notification that the previous page is no longer available.
3. Router falls back to the default post-login landing logic (home → default collection or manage-collections).

### A2: No Return Target Present

**Trigger:** The user opens the login page directly, or logs out manually rather than via session expiry (step 5)
**Flow:**

1. No `redirect` parameter exists.
2. System applies the default post-login landing logic unchanged.

### A3: Invalid or External Return Target

**Trigger:** The `redirect` parameter is malformed, an absolute URL, or points outside the app (step 6)
**Flow:**

1. System discards the return target (BR-099-1).
2. System applies the default post-login landing logic.
3. No error is shown — the user simply lands on the default page.

### A4: Return Target Is a Public or Login Route

**Trigger:** The captured target is itself the login, register, or another public page (step 6)
**Flow:**

1. System discards the target to avoid a redirect loop.
2. System applies the default post-login landing logic.

## Postconditions

### Success Postconditions

- The user is authenticated and back on the route they were on when the session expired, including query parameters (filters, search terms)
- No manual navigation was required to resume work

### Failure Postconditions

- If the return target is missing, invalid, or inaccessible, the user lands on the default post-login page — never on an error page and never outside the application
- Authentication itself is unaffected by any redirect failure

## Business Rules

### BR-099-1: Internal Routes Only (No Open Redirect)

The return target MUST be an application-internal route: a relative path resolving to a known route of this SPA. Absolute URLs, protocol-relative URLs, and paths outside the app MUST be discarded. This prevents the login page from being abused as an open redirector.

### BR-099-2: Survive the OIDC Round Trip

The return target must survive the full-page redirect to the identity provider and back (UC-033). It therefore cannot live only in in-memory state; it must be carried in the login route's URL or in client storage that persists across the round trip.

### BR-099-3: Route Only, Not Form State

Only the route (path + query) is restored. Unsaved in-page state — half-filled dialogs, text entered into forms — is explicitly out of scope for this use case.

### BR-099-4: Best Effort, Never Blocking

Restoring the previous page is a convenience. Any failure in capturing, preserving, validating, or navigating to the return target MUST degrade silently to the default landing behavior and MUST NOT interfere with login itself.
