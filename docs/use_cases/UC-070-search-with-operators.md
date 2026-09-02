# Use Case: Search Bookmarks with Operators

## Overview

**Use Case ID:** UC-070   
**Use Case Name:** Search Bookmarks with Operators   
**Primary Actor:** User   
**Goal:** Perform precise bookmark searches using a structured operator syntax in the search bar so that I can filter by tag, property, date, and folder without navigating through separate filter dialogs.   
**Status:** Done

## Traceability

**Maps to:** FR-071, FR-072, FR-073, FR-074, FR-103

---

## Preconditions

- The user is authenticated.
- The user has access to a collection containing bookmarks.

**Scope.** This grammar belongs to the **bookmark** search bar alone. Other search inputs in the app (collection management, dialog pickers) filter by plain substring, and must not inherit operators, autocomplete or the invalid-syntax flag — `Q3:Ops` is a name there, not a broken operator. The split is structural, not a flag: the shared `SearchBar` component knows no grammar, and `BookmarkSearchBar` adds it.

## Main Success Scenario

1. User clicks on the search bar (or activates it via keyboard shortcut: ⌘K / Ctrl+K or /).
2. User types a search query containing one or more operators (e.g., `#recipe created:>2026-01 folder:cooking`).
3. System parses the query into individual operators and free-text terms.
4. System filters the bookmark list in real time to show only bookmarks matching all operators and terms (AND logic).
5. System highlights matched operators in the search bar with visual distinction (e.g., colored tokens).
6. System displays the count of matching bookmarks.

### Supported Operators

- `#tagname` — filter by tag (exact match). Multi-word tags: `#"tag name"`.
- `tag:name` — alias for `#tagname`, identical semantics.
- `-#tagname` — exclude tag.
- `folder:name` — filter by folder name (substring match).
- `under:name` — filter by folder *subtree*: matches bookmarks in that folder or any descendant of it. Accepts a folder ID (the unambiguous form written by a sidebar click) or a folder name (case-insensitive; ambiguous when names repeat).
- `url:<url>` — filter by **exact URL**. Compares the normalized query value against the bookmark's normalized URL (BR-086), so `https://Example.com/a/` and `https://example.com/a` are the same URL, while `https://example.com/a/b` and `https://example.com/a?utm_source=x` are not. Quote the value if it contains spaces: `url:"..."`. The value must parse as an absolute URL — non-hierarchical schemes the API can store (e.g. `mailto:…`) included; anything else is invalid syntax (A2) and matches nothing.
- `property:value` — filter by property value (exact for select/boolean, substring for text). A bare key (`property:status`) is an existence check; a syntactically unparseable payload is invalid syntax (A2) and matches nothing.
- `created:YYYY-MM-DD` — filter by creation date. Supports `>` and `<` prefixes for ranges.
- `created:>today-Nd` — relative date filter (e.g., `created:>today-30d` for last 30 days). A value that does not parse as a date is invalid syntax (A2) and matches nothing.
- `note:keyword` — search within bookmark notes.
- `match:or` / `match:and` — how free-text terms combine (BR-081). `and` is the default; any other value is invalid syntax (A2).
- `-term` — exclude free-text term from results.

## Alternative Flows

### A1: Autocomplete Suggestion

**Trigger:** User types a trigger character (`#`, `folder:`, `propertyname:`, `note:`) followed by one or more characters (step 2).
**Flow:**

1. System displays a dropdown with matching tags, folders, or property values from the current collection.
2. User selects an item to insert it as a complete operator.
3. Use case continues at step 4.

### A2: Invalid Operator Syntax

**Trigger:** Query contains malformed operator syntax (step 3).
**Flow:**

1. System tints the search input's border destructive and renders the offending token as a warning-icon pill in the filter strip, carrying a tooltip with syntax help.
2. The whole query filters to nothing — invalid syntax is not partially applied, and negating it does not turn it back into a match (BR-070-2). A query that is merely unrecognised free text (an unknown `key:value`, see BR-070-2) is *not* invalid syntax and does not reach this flow.
3. Use case continues at step 4.

### A3: No Results

**Trigger:** No bookmarks match the combined operators (step 4).
**Flow:**

1. System displays empty list with message: "No bookmarks match your search."
2. Use case ends.

### A4: Combine with Sidebar Filters

**Trigger:** User has active tag or folder filters from the sidebar while typing operators (step 4).
**Flow:**

1. Sidebar filters and search operators combine with AND logic.
2. Use case continues at step 5.

### A5: Negation Operator

**Trigger:** User prefixes an operator with `-` (step 2).
**Flow:**

1. System excludes bookmarks matching that operator from results.
2. Use case continues at step 4.

### A6: Pasting a URL

**Trigger:** User pastes an absolute URL (e.g. `https://example.com/a`) into the search bar (step 2).
**Flow:**

1. System tokenizes the pasted value as a **single free-text term**, not as an operator (BR-070-2), so it filters by substring.
2. The autocomplete dropdown offers converting the term into an exact-URL query (`url:<pasted>`) but does not apply the conversion automatically (BR-088).
3. User accepts the conversion (or types `url:` themselves); use case continues at step 4.

### A7: Exact-URL Query Matches Nothing

**Trigger:** No bookmark's normalized URL equals the `url:` value (step 4).
**Flow:**

1. System displays the empty state (A3).
2. System offers the substring interpretation of the same value as a one-click fallback ("search anywhere for …"), so a near-miss (different query string, trailing path segment) is still findable.
3. Use case ends.

## Postconditions

### Success Postconditions

- The bookmark list shows only bookmarks matching all operators (and any active sidebar filters).

### Failure Postconditions

- An unparseable token filters the list to **nothing** and is flagged in the search bar — never a silently unfiltered list that looks like a result.
- An unquoted `key:value` with an unrecognised key is searched as free text (BR-070-2); it may legitimately return nothing, but it is not flagged and does not void the rest of the query.

## Business Rules

### BR-079: Operator Parsing

Operators are space-delimited. Quoted strings (`"..."`) preserve spaces within values. The parser must handle nested quotes gracefully.

### BR-070-1: The Operator Set Is Closed and Enumerable

The grammar has exactly one list of known operator keys — the ones documented under "Supported Operators" above. That list is defined once in the parser and consumed by the matcher, the autocomplete, and the invalid-syntax highlighting, so a new operator cannot be half-added. An operator that parses but does nothing (a no-op match-all) is not an operator: it is either implemented or it is unknown, and unknown keys follow BR-070-2.

### BR-070-2: Unknown Operators Never Match Everything

A `key:value` token whose key is outside the known set must never be treated as satisfied. It is re-interpreted as free text, or reported as invalid syntax (A2) and matched as false. This rule exists because the search bar's most common input — a pasted URL such as `https://example.com/a` — parses as `key=https`, and a match-all fallback turns it into a silently unfiltered list that is indistinguishable from a real result. Invalid syntax matches nothing **regardless of negation**: `-bogus:x` is not a vacuous "exclude nothing" that returns everything — the token is invalid, so the whole query filters to nothing.

Which of the two remedies applies is decided by **quoting**, because the colon is ordinary punctuation far more often than it is operator syntax:

| Input | Treatment | Rationale |
| --- | --- | --- |
| `Bug:123`, `localhost:5173/x`, `https://example.com/a`, `mailto:a@b` | **Free text** | An unquoted unknown key is a search term that happens to contain a colon. Voiding the query would make ordinary searches unusable, and a bare absolute URL is a free-text term, never an operator. |
| `bogus:"x y"` | **Invalid syntax (A2)** | Quoting a value is deliberate operator shape; nobody types it meaning a literal string, so it earns the flag. |
| `url:???`, `created:banana`, `property:=draft` | **Invalid syntax (A2)** | A *known* operator whose value does not parse is genuinely malformed. |

Consequence: a known operator keeps its meaning even when the value looks URL-shaped — `note://internal` searches notes for `//internal`, and `folder:"//shared"` survives a `stringifyTokens` → `tokenize` round trip.

### BR-080: Case-Insensitive Matching

Tag names, folder names, property names, and free-text terms are matched case-insensitively.

### BR-081: AND Logic Default

All operators and free-text terms are combined using AND logic by default. `match:or` switches the **free-text** terms to OR; structured operators (`#tag`, `folder:`, `url:`, …) always AND, whatever the mode. `match:and` states the default explicitly, so the mode can be flipped back without deleting the token.

```
#java match:or quarkus hibernate    → tagged #java AND (text ~ "quarkus" OR text ~ "hibernate")
folder:work match:or spring panache → in folder work AND (text ~ "spring" OR text ~ "panache")
```

Three rules make the mode unambiguous:

- **It is a setting, not a filter.** `match:` never selects or rejects a bookmark, and its position in the query is irrelevant — it governs terms that precede it as well as ones that follow. If several appear, the last one wins.
- **Exclusions stay unconditional.** `-draft` means the bookmark must not contain "draft", never "…or it may, provided another term matched". Only *positive* free-text terms are ORed.
- **An empty OR is not a filter.** `#java match:or` has no free-text terms to combine, so it filters exactly as `#java` does.

A `match:` value other than `and`/`or`, or a negated `-match:`, is invalid syntax (A2): a mode cannot be negated, and guessing at a typo'd mode would silently change which bookmarks are shown.

### BR-082: Client-Side Evaluation

Search operators are evaluated entirely client-side against data in the Pinia store, consistent with C-015. No server round-trip is required.

### BR-083: Autocomplete Triggers

Autocomplete dropdown appears for: `#` (tags), `folder:` and `under:` (folders), `property:` (property names, then values), `match:` (the two modes), and a pasted absolute URL (offering the `url:` conversion, BR-088). The dropdown shows up to 10 matching items.

Typing a prefix of an operator key offers the key itself (`fo` → `folder:`). That prefix discovery is driven by `OPERATOR_DEFS.discoverable`, whose requirement is a **hint string** to render beside the key — not value suggestions behind the colon. `url:` is discoverable and has no value list; `note:` and `created:` are free-typed and stay undiscoverable because there is nothing useful to say about them in one line. An operator whose value is a closed set (`match:`) must offer that set, since a half-typed value (`match:o`) is invalid syntax (A2) until it is completed.

### BR-084: Date Range Operators

Date operators support `YYYY-MM-DD`, `dd.MM.yyyy`, `today`, and `today-N[dwy]` formats (the `d` unit is the default for a bare `today-N`). The `>` prefix means "after", `<` means "before". Without a prefix, the operator matches the exact day. A value that does not parse is invalid syntax (A2) and matches nothing.

### BR-085: Relative Dates

Relative date expressions use the format `today-N[unit]` where N is a positive integer and the unit is `d` (days), `w` (weeks) or `y` (years). The unit is optional and defaults to days, so `today-30` and `today-30d` are equivalent. Example: `created:>today-30d` means "created in the last 30 days". Months are deliberately **not** a unit — "a month ago" has no unambiguous day count; use `today-30d` or an explicit date. A value outside these forms is invalid syntax (A2) and matches nothing, so this list and `RELATIVE_DATE_RE` in `searchQueryCreated.ts` must stay in step.

### BR-086: Exact-URL Match Is a Normalized Match

`url:` compares the **normalized** forms of both sides, using the same normalization already shared by client and server for duplicate detection (UC-096 BR-080): lowercase scheme and host, trailing slashes stripped (including a lone `/`), query parameters sorted, fragment dropped, tracking parameters (`utm_*`) kept, percent-encoding preserved byte-for-byte. Consequence: `https://Example.com/a/`, `https://example.com/a` and `https://example.com/a#top` are one and the same URL to this operator, while `?utm_source=x` is a different URL. There must be exactly one normalization implementation per side; `url:` must not grow a private variant of it.

### BR-087: URL Exactness Is Not Case Insensitivity

Scheme and host compare case-insensitively (normalization lowercases them); path and query compare case-**sensitively**, because servers may treat them so. This is a deliberate exception to BR-080, which continues to hold for tags, folders, properties, notes and free text.

**Known limitation — userinfo is not compared.** The shared normalizer builds its result from scheme + host, so any `user@` (or `user:pass@`) credential in the authority is dropped on both sides: `https://alice@example.com/a`, `https://bob@example.com/a` and `https://example.com/a` are one URL to `url:`, and so are `mailto://alice@example.com` and `mailto://bob@example.com`. (`mailto:alice@example.com` *without* the `//` is unaffected — the address lands in the path and compares exactly.) This is inherited from duplicate detection, where it is the conservative choice, and the backend `ImportUrlNormalizer` behaves identically. Fixing it means changing both implementations together per BR-086 — `url:` must not grow a private variant.

### BR-088: Paste Is Guided, Not Guessed

When the token under the cursor parses as an absolute URL, the autocomplete offers the `url:` conversion but does **not** apply it automatically: a pasted URL keeps its substring semantics unless the user opts in. Silent rewriting of what the user typed is prohibited — the user must be able to see, in the search bar, which query is running.
