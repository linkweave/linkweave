# Use Case: Subscribe to Collection RSS Feed

## Overview

**Use Case ID:** UC-101   
**Use Case Name:** Subscribe to Collection RSS Feed   
**Primary Actor:** Collection User (any role with access to the collection)   
**Goal:** Follow new bookmarks added to a collection from an external feed reader, without logging in to LinkWeave.   
**Status:** Draft   

## Traceability

**Notes:** Competitive parity with Karakeep, which can generate RSS feeds from
lists (see `docs/marketing/market-analysis.md`). A feed reader acts as a
secondary actor: it polls the feed URL unauthenticated, so access is protected
by an unguessable per-user token instead of a session.

---

## Preconditions

- The actor is authenticated.
- The actor has access to the collection (any role, including Viewer per UC-100).

## Main Success Scenario

1. Collection User opens the collection settings and selects the "RSS Feed" section.
2. System shows that no feed URL exists yet for this user and offers to generate one.
3. Collection User chooses to generate the feed URL.
4. System generates a unique, unguessable feed URL tied to this user and this collection.
5. System displays the feed URL with a copy-to-clipboard action and a note that anyone holding the URL can read the feed.
6. Collection User copies the URL into their feed reader.
7. Feed reader requests the URL without authentication.
8. System returns a valid RSS document containing the most recently added bookmarks of the collection, each item carrying the bookmark title, target URL, description, tags, and the date it was added.
9. Feed reader polls periodically; newly added bookmarks appear as new feed items.

## Alternative Flows

### A1: Regenerate Feed URL

**Trigger:** Collection User suspects the feed URL leaked, or wants to rotate it (step 2, when a URL already exists).
**Flow:**

1. System shows the existing feed URL with a "Regenerate" action.
2. Collection User confirms regeneration.
3. System replaces the token; the previous feed URL immediately stops working.
4. Use case continues at step 5 with the new URL.

### A2: Disable Feed

**Trigger:** Collection User no longer wants a feed for this collection (step 2, when a URL already exists).
**Flow:**

1. Collection User chooses to disable the feed.
2. System deletes the feed token; subsequent requests to the old URL are rejected.
3. Use case ends.

### A3: Feed Requested With Invalid or Revoked Token

**Trigger:** A request arrives with a token that is unknown, regenerated, or disabled (step 7).
**Flow:**

1. System rejects the request without revealing whether the collection exists.
2. No feed content is returned.

### A4: Collection Access Revoked

**Trigger:** The user's access to the collection is revoked or the collection is deleted after a feed URL was generated (any time after step 4).
**Flow:**

1. System invalidates the user's feed token for that collection.
2. Subsequent feed reader requests are rejected as in A3.

## Postconditions

### Success Postconditions

- A feed token exists for this user and collection.
- The feed URL returns a valid RSS document reflecting the collection's most recently added bookmarks.
- New bookmarks added to the collection appear in the feed on subsequent polls.

### Failure Postconditions

- No feed token is created or the previous token remains unchanged.
- No collection content is exposed to unauthenticated callers.

## Business Rules

### BR-101-1: Token-Scoped, Unauthenticated Read Access

The feed is the only unauthenticated read path into a collection. It is
reachable solely via an unguessable token and exposes nothing beyond the feed
items themselves (no member list, no collection settings, no properties).

### BR-101-2: Token Bound to User Access

Each feed token belongs to one user and one collection. Revoking the user's
collection access, deleting the collection, or deleting the user invalidates
the token. Tokens of other users on the same collection are unaffected.

### BR-101-3: Bounded Feed Size

The feed contains only the most recently added bookmarks up to a fixed limit
(default: 50 items), ordered newest first. It is a notification stream, not an
export mechanism.

### BR-101-4: Read-Only Representation

The feed never exposes write operations. Trashed bookmarks and bookmarks
removed from the collection disappear from the feed on the next poll.
