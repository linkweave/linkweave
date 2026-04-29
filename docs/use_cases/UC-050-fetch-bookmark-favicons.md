# Use Case: Fetch Bookmark Favicons

## Overview

**Use Case ID:** UC-050
**Use Case Name:** Fetch Bookmark Favicons
**Primary Actor:** User
**Secondary Actor:** Collection Owner (configures internal-domain allowlist)
**Goal:** Display recognisable favicons next to bookmarks so that the user can scan a list visually, while preventing the backend from being abused as an SSRF vector and while still working for bookmarks that point to internal hosts the backend cannot reach.
**Status:** Implemented

## Traceability

**Maps to:** FR-063

---

## Preconditions

- The user is authenticated.
- The user has access to at least one collection containing bookmarks with valid HTTP(S) URLs.

## Main Success Scenario

1. User opens a collection and the system renders the bookmark list.
2. For each bookmark URL, the frontend determines whether the host matches one of the **internal-domain allowlist** patterns configured on the collection.
3. **External host (no match):**
   1. Frontend requests the icon via the backend favicon proxy `GET /api/collections/{collectionId}/bookmarks/{bookmarkId}/favicon`.
   2. Backend resolves the bookmark URL's host, refuses if it resolves to a private, loopback, link-local, or metadata-service IP (SSRF guard).
   3. Backend fetches `/favicon.ico` (with timeouts, max-size, redirect re-validation, content-type allowlist `image/*`), stores the bytes in the on-disk favicon cache directory under a hash-derived filename, and streams the bytes back.
   4. Frontend renders the returned image.
4. **Internal host (match):**
   1. Frontend renders an `<img>` tag pointing directly at `https://<host>/favicon.ico` with `referrerpolicy="no-referrer"`.
   2. The user's browser, which sits inside the internal network, fetches the icon directly.
5. Bookmarks for which no favicon could be obtained fall back to a generic placeholder icon.

## Alternative Flows

### A1: Backend Cannot Reach External Host

**Trigger:** The proxy fetch in step 3.iii fails (timeout, DNS failure, non-2xx response).
**Flow:**

1. Backend writes a "no favicon" marker file (zero-byte sentinel or metadata flag) into the favicon cache directory with a short TTL to avoid retry storms.
2. Backend returns HTTP 204.
3. Frontend renders the generic placeholder.
4. Use case ends.

### A2: SSRF Guard Triggered

**Trigger:** The bookmark URL resolves to a disallowed IP range (private, loopback, link-local, `169.254.169.254`).
**Flow:**

1. Backend refuses to fetch and returns HTTP 422 with a generic error code (no internal details echoed).
2. Frontend renders the generic placeholder.
3. Use case ends.

### A3: Internal Allowlist Pattern Matches but Browser Cannot Reach Host

**Trigger:** The user is outside the internal network (e.g. working from home without VPN).
**Flow:**

1. Browser fetch fails; the `<img>` element fires its `error` event.
2. Frontend renders the generic placeholder.
3. Use case ends.

### A4: Owner Edits the Internal-Domain Allowlist

**Trigger:** The Collection Owner opens collection settings and edits the favicon-allowlist field.
**Flow:**

1. System displays the current allowlist as an editable multi-line field (one pattern per line).
2. Owner adds/removes patterns and confirms.
3. System validates each pattern (lowercase, no scheme, no path, optional leading `*.`, rejects bare IPs and `*` alone).
4. System persists the updated allowlist on the `Collection`.
5. All users with access to the collection see the new dispatch behaviour after their next page load.

### A5: Non-Owner Attempts to Edit Allowlist

**Trigger:** A user with shared (non-owner) access tries to modify the allowlist (step A4.2).
**Flow:**

1. System denies the change and displays an error: "Only the collection owner can edit the favicon allowlist."
2. Use case ends.

### A6: Invalid Allowlist Pattern

**Trigger:** A pattern submitted in A4.3 fails validation.
**Flow:**

1. System rejects the save and reports which pattern is invalid and why.
2. Use case continues at A4.2.

## Postconditions

### Success Postconditions

- The bookmark list renders with favicons where available and with placeholders elsewhere.
- The backend has not been induced to make requests to disallowed network ranges.
- The internal-domain allowlist (if edited) is persisted on the collection and shared with everyone who has access to it.

### Failure Postconditions

- A generic placeholder icon is shown; bookmark functionality (open, edit, delete) is unaffected.

## Business Rules

### BR-100: SSRF Guard on Favicon Proxy

The backend favicon proxy must reject any URL whose host resolves to a loopback (`127.0.0.0/8`, `::1`), private (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `fc00::/7`), link-local (`169.254.0.0/16`, `fe80::/10`), or cloud-metadata (`169.254.169.254`) address. Hostname resolution must be re-checked after every redirect.

### BR-101: Allowed Schemes and Content Types

Only `http://` and `https://` URLs may be proxied. Only responses with `Content-Type: image/*` and a body smaller than the configured maximum (default 256 KiB) may be returned to the client.

### BR-102: Owner-Only Allowlist Edit

Only the Collection Owner may edit the collection's favicon-allowlist field.

### BR-103: Allowlist Pattern Validation

Patterns must be lowercase domain suffixes with no scheme and no path. A leading `*.` wildcard is permitted. Bare IP addresses, the bare wildcard `*`, and patterns containing `/` or `:` are rejected.

### BR-104: Allowlist Scope

The allowlist on a collection applies only to bookmarks within that collection. Allowlists are not unioned across collections, so a user must configure each collection they own.

### BR-105: CSP Posture for Favicons

The application's `img-src` directive permits `'self'`, `data:`, and `https:` so that direct `<img>` loads against internal hosts work without runtime CSP changes. The privacy and SSRF properties of this feature are enforced by the dispatch logic and the backend proxy, not by CSP.

### BR-106: Favicon Cache Storage Location and Layout

Cached favicons are stored on the local filesystem, **not** in the database. The cache directory location is a configurable application property (default `developer-local-settings/favicon-cache/`). Each cache entry is named after the SHA-256 hash of the canonical (lowercased, scheme-normalised, path-stripped) origin of the bookmark URL, so all bookmarks pointing at the same origin share a single cached icon. A sidecar metadata file (or a small index) records `contentType`, `fetchedAt`, and a "no favicon" marker for negative caching.

### BR-107: Favicon Cache Lifecycle

Cached entries expire after a configurable TTL (default 30 days for successful fetches, 6 hours for "no favicon" markers). Expired entries are refreshed on next request. The cache is not cascaded on bookmark or collection deletion — entries are keyed by origin, shared across bookmarks, and may legitimately outlive any single bookmark. A periodic janitor task removes entries that have not been read for longer than the TTL. The cache directory is safe to delete in full at any time; it will be re-populated lazily.
