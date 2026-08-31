# Use Case: Search Bookmarks by Exact URL

## Overview

**Use Case ID:** UC-107
**Use Case Name:** Search Bookmarks by Exact URL
**Primary Actor:** User
**Goal:** Paste (or type) a full URL into the search bar and see exactly the bookmark(s) that store that URL — so that I can answer "have I already saved this page, and where did I file it?" without scrolling, and without the query silently matching everything.
**Status:** Draft

## Traceability

**Maps to:** FR-103
**Related:** UC-070 (Search with Operators — the grammar this operator joins); UC-053 (Warn on Duplicate Bookmark) and UC-096 (Review and Select Bookmarks Before Import) — both already define URL equivalence, which this use case reuses; UC-106 (Copy Bookmark URL to Clipboard) — produces the value that gets pasted back in; UC-071 (Save Search Query); UC-032 (Search Bookmarks)

---

## Background

Two gaps make "find this exact URL" unreliable today:

1. **There is no exact-URL operator.** A URL typed as free text matches by substring against title, URL, description and tag names, so `https://example.com/a` also matches `https://example.com/a/b`, `.../a?ref=x`, and any bookmark whose *description* quotes the link. There is no way to ask for "this URL and nothing else".
2. **A pasted URL is parsed as an operator and matches everything.** The tokenizer's operator rule is `([a-z]+):(\S+)` (`frontend/src/lib/searchQuery.ts:100`), so `https://example.com/a` tokenizes as the operator `https` with value `//example.com/a`. Unknown operator keys fall through `bookmarkMatchesOperator`'s `default: return true` (`frontend/src/lib/searchQuery.ts:255`), i.e. the token matches *every* bookmark. Pasting a URL into the search bar therefore returns the unfiltered collection instead of the one bookmark being looked for — silently, with no invalid-syntax feedback.

Fixing (2) is a precondition for (1): an exact-URL operator is worthless if the bare paste — the way users actually search for a URL — still degrades to match-all.

## Preconditions

- The user is authenticated and viewing a collection they have access to (UC-024).
- The collection's bookmarks are loaded in the client store (C-015).

## Main Success Scenario

1. User copies a URL (e.g. via UC-106, or from the browser address bar).
2. User activates the search bar (⌘K / Ctrl+K or `/`) and pastes the URL.
3. System tokenizes the pasted value as a **single free-text term**, not as an operator (BR-107-2).
4. System recognizes that the term is a URL and offers, in the autocomplete dropdown, to convert it into an exact-URL query (`url:<pasted>`), alongside the plain substring interpretation (BR-107-6).
5. User accepts the exact-URL suggestion (or types `url:` themselves).
6. System normalizes the operator value and every candidate bookmark's URL with the shared normalization contract (BR-107-1) and keeps only bookmarks whose normalized URL is equal to the normalized query value.
7. System displays the matching bookmark (usually exactly one) with the operator rendered as a highlighted token and the result count shown, per UC-070 steps 5–6.

## Alternative Flows

### A1: No Bookmark Has That URL

**Trigger:** No bookmark's normalized URL equals the query value (step 6).
**Flow:**

1. System displays the standard empty state ("No bookmarks match your search.", UC-070 A3).
2. System offers the substring interpretation of the same value as a one-click fallback ("search anywhere for …"), so a near-miss (different query string, trailing path segment) is still findable.
3. Use case ends.

### A2: User Wants Partial URL Matching

**Trigger:** User wants every bookmark under a path or host, not one exact URL (step 5).
**Flow:**

1. User leaves the value as free text instead of accepting the `url:` conversion.
2. System matches by substring against title, URL, description and tags, as today.
3. Use case continues at step 7.

### A3: Value Is Not a Parseable URL

**Trigger:** The `url:` operator's value cannot be parsed as an absolute URL (e.g. `url:` with an empty value, or `url:???`) (step 6).
**Flow:**

1. System treats the token as invalid syntax: it underlines the token and shows the syntax hint (UC-070 A2).
2. The token matches **nothing** rather than everything (BR-107-3); the remaining tokens still filter.
3. Use case continues at step 7.

### A4: Exclusion

**Trigger:** User prefixes the operator with `-` (`-url:https://example.com/a`) (step 5).
**Flow:**

1. System excludes bookmarks whose normalized URL equals the value, keeping the rest.
2. Use case continues at step 7.

### A5: The URL Contains Spaces or Quotes

**Trigger:** The URL (typically an un-encoded one pasted from a document) contains whitespace (step 5).
**Flow:**

1. User (or the autocomplete conversion in step 4) wraps the value in double quotes: `url:"https://example.com/a b"`.
2. System parses the quoted value as one operator value, per UC-070 BR-079.
3. Use case continues at step 6.

### A6: Query Is Saved or Shared

**Trigger:** User saves the query (UC-071) or it backs a smart collection (UC-072).
**Flow:**

1. System round-trips the `url:` token through `stringifyTokens` unchanged, re-quoting only when needed.
2. Re-running the saved query later yields the same result set.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks whose URL is equal — under the shared normalization contract — to the searched URL, combined with any other active operators and sidebar filters (AND, per UC-070 BR-081).
- Pasting any URL into the search bar never returns the unfiltered collection.

### Failure Postconditions

- An unparseable or unknown token filters the list to nothing and is flagged as invalid; the user is never shown a silently unfiltered list that looks like a result.

## Business Rules

### BR-107-1: Exact Match Is Normalized Match

`url:` compares the **normalized** forms of both sides, using the same normalization already agreed between client and server for duplicate detection (UC-096 BR-080: `frontend/src/lib/url.ts#normalizeUrl` and `ImportUrlNormalizer.normalize`): lowercase scheme and host, trailing slashes stripped (including a lone `/`), query parameters sorted, fragment dropped, tracking parameters (`utm_*`) **kept**, percent-encoding preserved byte-for-byte. Consequence: `https://Example.com/a/`, `https://example.com/a` and `https://example.com/a#top` are one and the same URL to this operator, while `?utm_source=x` is a different URL. There must be exactly one normalization implementation per side; `url:` must not grow a private variant of it.

### BR-107-2: A Bare URL Is Free Text, Never an Operator

A token whose text parses as an absolute URL (has a scheme followed by `//`, e.g. `https://…`, `http://…`) is a single free-text term. The operator rule must not claim `https:`/`http:` (or any scheme) as an operator key. Pasting a URL therefore behaves like any other search term, and — per BR-107-3 — can never match-all.

### BR-107-3: An Unknown Operator Never Matches Everything

A `key:value` token whose key is not in the known-operator set (UC-070, "Supported Operators") must not be silently treated as satisfied. It is either (a) re-interpreted as free text, or (b) reported as invalid syntax per UC-070 A2 and matched as **false**. A match-all fallback is prohibited, because it turns a typo into a silently unfiltered list that is indistinguishable from a legitimate result.

### BR-107-4: Exactness Is Not Case Sensitivity

Scheme and host compare case-insensitively (normalization lowercases them); path and query compare case-**sensitively**, because servers may treat them so. This is a deliberate exception to UC-070 BR-080 (case-insensitive matching), which continues to hold for tags, folders, properties, notes and free text.

### BR-107-5: Client-Side, Like the Rest of the Grammar

`url:` is evaluated client-side against the loaded store, per C-015 and UC-070 BR-082. It adds no endpoint and no server round-trip.

### BR-107-6: Paste Is Guided, Not Guessed

When the token under the cursor parses as an absolute URL, the autocomplete offers the `url:` conversion but does **not** apply it automatically: a pasted URL keeps its substring semantics unless the user opts in. Silent rewriting of what the user typed is prohibited — the user must be able to see, in the search bar, which query is running.

### BR-107-7: One Definition of the Operator Set

The known-operator set is defined once (the parser's operator table) and consumed by the matcher, the autocomplete (`OPS`), and the invalid-syntax highlighting. Adding an operator must not require touching three lists — that drift is what left `under:` and `tag:` undocumented while `property:` and `match:` parse but do nothing.

## Acceptance Criteria

1. Pasting `https://example.com/a` into an empty search bar filters the list to bookmarks containing that string; it never leaves the list unfiltered.
2. `url:https://example.com/a` matches a bookmark stored as `https://Example.com/a/` and does **not** match one stored as `https://example.com/a/b` or `https://example.com/a?utm_source=x`.
3. `url:https://example.com/a?b=2&a=1` matches a bookmark stored as `https://example.com/a?a=1&b=2`.
4. `-url:<x>` returns the complement of rule 2's result set.
5. `bogus:value` returns no bookmarks and is flagged invalid — it does not return all bookmarks.
6. A `url:` query survives save → reload → re-run (UC-071) with an identical result set.
7. Copy URL (UC-106) → paste into search → accept the `url:` conversion finds exactly the bookmark it was copied from.

## Notes / Future Considerations

- **`host:` / `domain:` deferred.** "Every bookmark on this host" is a plausible next operator, but it is a different question from "this exact page" and would need its own rules for subdomains and `www.`. Free-text substring covers the common case today.
- **Duplicate warning reuse.** UC-053 answers the same question at create time. Once `url:` exists, the duplicate warning should link to the `url:` query for the conflicting bookmark rather than only naming it.
