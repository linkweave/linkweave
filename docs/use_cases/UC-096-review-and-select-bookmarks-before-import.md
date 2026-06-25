# Use Case: Review and Select Bookmarks Before Import

## Overview

**Use Case ID:** UC-096
**Use Case Name:** Review and Select Bookmarks Before Import
**Primary Actor:** User
**Goal:** Inspect the contents of a parsed bookmark file and deselect folders, individual bookmarks, and known duplicates before anything is written, so I import only what I want.
**Status:** Proposed

## Traceability

**Extends:** UC-031 (Import Browser Bookmarks), FR-031
**Reuses:** UC-074 selection model (`stores/selection.ts`), BR-080 URL normalization (`lib/url.ts`)
**Supersedes:** BR-061 (duplicates silently allowed) and the root-only placement of BR-064 — see Business Rules below.

---

## TL;DR — the one change

Import already exists (UC-031): today the flow is effectively *pick file → import everything*. This adds **one intermediate step** — after the file is parsed, show the user what's inside and let them **deselect folders or individual bookmarks** (and skip ones already in their library) before anything is written.

```
  before:  [ pick file ] ───────────────────────────────▶ [ import all ]
  after:   [ pick file ] ─▶ [ REVIEW: tree + checkboxes ] ─▶ [ import selected ]
```

"Import everything" is still one click (everything is pre-selected). Selection is **transient** — it lives only for the review and is discarded unless **Import** is pressed.

## Why

A browser export is messy — dead tabs, work folders that don't belong in a personal collection, duplicates of things already saved. Importing wholesale leaves the user with cleanup to do, and the screenshot-capture pipeline burns work on bookmarks they'll delete. Reviewing **before** commit is cheaper for the user and the backend.

---

## Preconditions

- The user is authenticated.
- The user has write access to the target collection.
- The user has exported bookmarks from their browser as a Netscape Bookmark HTML file (`.html` / `.htm`, ≤ 5 MB — BR-065).

## Main Success Scenario

1. User opens the existing **Data tab** in the collection settings dialog (`components/bookmark/CollectionSettingsModal.vue`) and chooses a bookmarks HTML file.
2. System validates the file (non-null, `.html`/`.htm`, size ≤ 5 MB) and navigates to the review surface at `/collections/:id/import`.
3. System calls `POST /collections/:id/import/preview` (multipart) and renders a skeleton tree with "Reading bookmarks…".
4. System returns a manifest: the folder/bookmark tree (stable ids), totals, and a server-computed `duplicate` flag on each bookmark node already present in the destination collection.
5. System renders the **Tree** with all folders expanded; everything is pre-selected **except** flagged duplicates. Duplicates show an amber **`ⓘ In library`** badge and are dimmed and pre-deselected while "skip" is on.
6. User reviews and adjusts the selection — toggling individual bookmarks, whole folders (tri-state), `Select all` / `Clear`, and the `Skip N already in library` pill.
7. (Optional) User changes **`Import into ▸ [Collection]`** and/or a target parent folder.
8. The footer states the consequence — "N bookmarks will be imported into {collection}" — and the primary button reads **`Import N bookmarks`** (disabled at 0).
9. User presses Import. System calls `POST /collections/:id/import/commit` with the pruned tree of kept `nodes`, the optional `destinationFolderId`, and `skipDuplicates`.
10. System recreates the needed folder paths (merging into an existing same-named folder at the same path), creates the selected bookmarks, and **queues each new bookmark into the screenshot/capture pipeline**.
11. System returns `{ imported, foldersCreated, skipped }`, invalidates `folderStore` / `bookmarkStore`, shows the toast `Imported N bookmarks into {collection}.`, and routes to the destination collection.

## Alternative Flows

### A1: Empty or unsupported file
**Trigger:** The parsed file contains no bookmarks (step 4).
**Flow:** System shows "No bookmarks found in this file." plus the accepted format and a re-pick button. The dropzone stays — never a dead end.

### A2: Malformed file
**Trigger:** The file cannot be parsed as Netscape HTML (step 4).
**Flow:** System shows an error card and keeps the dropzone.

### A3: All duplicates
**Trigger:** Every bookmark in the file already exists in the destination (step 5).
**Flow:** Tree renders all-dimmed; the primary button reads `Import 0` (disabled) with a hint to turn off "skip".

### A4: Large import (> 500 selected)
**Trigger:** User commits more than 500 bookmarks (step 9).
**Flow:** The screen stays; the footer swaps for inline progress (`Importing 1,240… 500/1,240`), actions disabled. No modal. (The 500-row batch ceiling is the same C-017 limit UC-074 uses; if the commit endpoint enforces it, page the commit.)

### A5: Leaving the review
**Trigger:** User navigates away before pressing Import.
**Flow:** The transient store is cleared; nothing is written.

### A6: No collection access
**Trigger:** User lacks write access to the destination (step 3 or 9).
**Flow:** HTTP 403; "You do not have permission to modify this collection." No changes.

## Postconditions

### Success
- Only the kept bookmarks exist in the destination collection, under their recreated/merged folder paths.
- Captures are queued only for the kept bookmarks.
- `folderStore` / `bookmarkStore` are invalidated and the user is on the destination collection.

### Failure
- Nothing is written (atomic — BR-062 still holds for the commit).

---

## Selection model (shared by every layout)

Same spirit as UC-074's batch selection (`stores/selection.ts`): a transient `Set<bookmarkId>`, never persisted, cleared on leave. **Note:** UC-074's store is *flat* over `filteredBookmarks` and has no folder/tri-state derivation, so this is a **new composable that follows the same pattern**, not a literal lift. The `'mixed'` tri-state precedent lives in `components/bookmark/batchTagModel.ts`.

- **State:** `selected: Set<bookmarkId>`. Folders hold no state of their own.
- **Folder state is derived:** `none` (0 descendants selected) · `some` (partial → `–` dash) · `all` (→ check).
- **Toggle a bookmark:** add/remove its id.
- **Toggle a folder:** if every descendant is selected → deselect the whole subtree; otherwise → select the whole subtree.
- **Counts:** every folder row shows `selected/total` of its descendant bookmarks; full = just the total.
- **Bulk:** `Select all` / `Clear` in the header.

```ts
// derived folder state
function folderState(node, selected): 'none' | 'some' | 'all' {
  const ids = bookmarkIdsUnder(node)
  const on = ids.filter(id => selected.has(id)).length
  return on === 0 ? 'none' : on === ids.length ? 'all' : 'some'
}
```

## Layout — Tree only

**Ship one layout: the Tree.** A nested disclosure tree; folder rows have a chevron + tri-state checkbox + count badge (`5/6`), bookmarks indent beneath. It reuses the disclosure mental model of the sidebar `FolderTree` (which already default-expands), so there's nothing new to learn. Default-expand folders.

> The prototype's Columns and Grouped layouts were explorations — **not v1.** Because everything renders from one selection model (a `Set` of ids + derived folder state), a future layout is pure presentation and can be added without touching state. Note it as a possible follow-up, not v1 scope.

## Duplicate handling

A bookmark whose normalized URL already exists in the **destination collection** is a duplicate.

- Rendered with an amber **`ⓘ In library`** badge and, when "skip" is on, dimmed + pre-deselected.
- Header shows a **`Skip N already in library`** pill (on by default). Toggling it adds/removes all duplicate ids in one move — the user can still hand-pick any individual duplicate back in.
- **Who computes it — and the per-node flag:** the **backend**, because the client may only hold a page of the library. The preview sets a **`duplicate: true` flag on each bookmark node** (plus a `duplicateCount` total). The client reads that flag directly and does **not** re-normalize URLs itself.
  > **⚠️ Why per-node, not a `duplicateUrls` set:** the first cut returned a set of normalized URLs and had the client re-normalize each node URL (with JS `lib/url.ts`) to test membership. That silently failed whenever the Java and JS normalizers disagreed — most visibly for **percent-encoded queries** (Java `URI.getQuery()` *decodes*; JS `URL.search` stays *encoded*), so re-importing the same file left those bookmarks marked "new". Computing the flag once, server-side, removes the entire cross-language parity surface.
- **Normalization:** the backend `ImportUrlNormalizer` mirrors `normalizeUrl` (BR-080, `lib/url.ts`): lowercase scheme + host, strip trailing slashes (including a lone root `/`), sort query params, drop the fragment, and operate on the **raw (still percent-encoded)** path/query so encoded URLs round-trip exactly. **utm note:** neither normalizer strips `utm_*` — kept and sorted, deliberately (a near-dup slipping through is safer than hiding a wanted bookmark).
- **Un-importable URLs:** non-web schemes the platform can't store (e.g. `chrome://…`) are **excluded from the preview manifest** and reported via `unsupportedCount`, so they never inflate the "N will be imported" promise and the post-commit `imported` count always reconciles. The UI shows a small note ("N items can't be imported…").

## Destination

Header control **`Import into ▸ [Collection]`**, defaulting to `collectionStore.currentCollectionId`. Optionally allow a target **parent folder** (imported folders nest under it). Folder paths from the file are recreated under the destination, **merging into an existing same-named folder at the same path** rather than creating `Work (2)`. (This is a behavior change from UC-031 — see BR-064 below.)

## States

| State | Treatment |
|---|---|
| Parsing | Skeleton tree + "Reading bookmarks…"; parse is fast but a 5 MB file isn't instant |
| Empty / unsupported | "No bookmarks found in this file." + accepted format + re-pick button |
| All duplicates | Tree all-dimmed; primary button `Import 0` (disabled) with a hint to turn off "skip" |
| Malformed | Error card, keep the dropzone |
| Large (> 500 selected) | Keep the screen; inline progress on commit, actions disabled; no modal |
| Success | Toast `Imported N bookmarks into {collection}.` → navigate to the collection |

Footer always states the consequence: **"N bookmarks will be imported into {collection}"**; primary button `Import N bookmarks`, disabled at 0.

---

## Backend contract

> **⚠️ Correction to the original handoff:** today there is **one** all-in-one endpoint (`POST /collections/{id}/import` → `BookmarkImportService`, which writes everything immediately). There is **no** separate parse/commit split and **no** dedup. So this is **not** "the endpoints stay and a screen slots between them" — the single endpoint is **replaced by** the two below. The new behavior (preview manifest, dedup, folder merge-by-path) is all net-new.

```
POST /collections/:id/import/preview        (multipart: file)
  → 200 {
      tree: ImportNode[],          // folders + bookmarks, stable ids;
                                   //   each bookmark node carries duplicate:boolean
      totalBookmarks, totalFolders,    // totals reflect only importable bookmarks
      duplicateCount,                  // how many bookmark nodes are duplicates
      unsupportedCount                 // bookmarks excluded (un-storable URL)
    }

POST /collections/:id/import/commit
  → body {
      destinationFolderId?: string,    // 400 if it doesn't exist / wrong collection
      skipDuplicates: boolean,
      fileName?: string,               // recorded as the import-source property
      nodes: ImportNode[]              // the PRUNED tree of kept folders + bookmarks
    }
  → 200 { imported, foldersCreated, duplicatesSkipped }
```

Recursion (both phases) is depth-capped to guard the request thread's stack against a hostile deeply-nested payload. Bookmark provenance: when bookmark properties are enabled, each created bookmark gets an `import-source` property holding `fileName` — parity with UC-031.

`ImportNode`: `{ id, type: ImportNodeType (FOLDER | BOOKMARK), name, url?, addDate?, duplicate, children? }`.

> **Why `nodes`, not `bookmarkIds`:** the original handoff sent only the kept ids. That would force the server to *remember* the parsed file between the two calls (server-side session state) — but selection is explicitly transient and client-side (BR-184). Sending the pruned tree instead keeps the commit **stateless**: the client already holds the full manifest, so it returns the selected subtree and the server writes exactly what it receives (applying folder merge-by-path and, defensively, the duplicate skip). No re-upload, no cached parse.
>
> **Naming note:** the existing `ImportSummaryJson.skipped` counts **invalid-URL** skips (see `BookmarkImportService.createBookmark`), not duplicate skips — hence the distinct `duplicatesSkipped` field here.

Parsing can stay client-side (`DOMParser` over the Netscape `<DT>`/`<H3>`/`<A>` structure) if preferred — but dedup still needs the server, so a preview endpoint that does both is cleaner. Authorization stays in the resource layer via `AuthorizationService.requireCollectionAccess` (as `ImportResource` does today).

## Frontend pieces

| File | Purpose |
|---|---|
| `views/ImportReviewView.vue` | Route shell: header (file meta, destination, summary, bulk actions), body, footer. Route `/collections/:id/import`, rendered outside `MainLayout`'s content column with its own header — same pattern as `CleanupSuggestionsView.vue`. |
| `components/import/ImportTree.vue` + `ImportTreeNode.vue` | The Tree layout (the only layout for v1). |
| `composables/useImportSelection.ts` | The `Set` + tri-state model (same pattern as UC-074; tri-state from `batchTagModel.ts`). |
| `stores/import.ts` | Transient parsed tree + selection (cleared on leave). |

## On commit

- Create missing folders (merge by path), then create the selected bookmarks under them.
- **Queue each new bookmark into the existing screenshot/capture pipeline** (`ScreenshotCaptureJobService`) — the payoff of reviewing first: captures only run for kept bookmarks.
- Invalidate `folderStore` / `bookmarkStore`, toast, route to the destination collection.

---

## Theming — use tokens, not hex (light + dark)

> **⚠️ Major correction.** The original handoff shipped a hardcoded hex token table whose **dark values matched the app but whose light values were generic Tailwind grays** (`#f9fafb`, `#111827`, `#6b7280`, `#e5e7eb`). The app's light theme is a **warm cream** palette (`#f7f6f2`, `#1b1916`, `#6f695d`, `#e7e2d8`). Hardcoding the handoff's light hexes would make this screen read cool-grey against a warm UI. **Do not hardcode any hex.**

Build with the app's Tailwind `@theme` tokens (defined once in `src/assets/main.css`, with a `.dark` override block) so both themes come for free. Use utility classes, not raw scoped CSS:

| Need | Use (utility) | CSS var |
|---|---|---|
| surface / card | `bg-card` | `--color-card` |
| page bg | `bg-background` | `--color-background` |
| text | `text-foreground` | `--color-foreground` |
| muted text / counts | `text-muted-foreground` | `--color-muted-foreground` |
| borders | `border-border` | `--color-border` |
| primary (checkbox fill, primary button) | `bg-primary` / `text-primary` | `--color-primary` |

**Tri-state checkbox — corrected.** The original `.cl-check` snippet referenced `var(--primary)`, which **does not exist** (the token is `--color-primary`), so the fill would never render. There is also **no existing tri-state checkbox component** to reuse — `FolderTreeNode` has expand/collapse only, no checkboxes, no `aria-checked`. Build it net-new with utilities:

```html
<!-- state: 'none' | 'some' | 'all' -->
<span
  role="checkbox"
  :aria-checked="state === 'some' ? 'mixed' : state === 'all'"
  class="grid h-[17px] w-[17px] place-items-center rounded-[5px] border-[1.5px]"
  :class="state === 'none'
    ? 'border-muted-foreground'
    : 'border-primary bg-primary text-primary-foreground'"
>
  <!-- all → check icon · some → minus icon · none → empty -->
</span>
```

**Duplicate `ⓘ In library` badge (amber).** The app has **no amber/info token** — the closest, `--color-remove` (burnt orange), is semantically "remove" and must not be reused. Add a dedicated token to both blocks in `main.css` so it's themable:

```css
/* @theme (light) */ --color-duplicate: #b45309; /* amber-700, warm-tuned for contrast on cream */
/* .dark        */   --color-duplicate: #e0a350; /* matches the handoff's amber */
```

Background uses the same hue at low alpha (e.g. `color-mix(in oklch, var(--color-duplicate) 12%, transparent)`).

## Accessibility

- Tree: `role="tree"` / `treeitem`, folder rows `aria-expanded`; checkboxes `role="checkbox"` with **`aria-checked="mixed"`** for partial folders.
- Counts announced via `aria-label` on the folder row ("Work, 6 of 7 selected").
- Full keyboard path: arrows move, `space` toggles, `←/→` collapse/expand; `Select all` / `Clear` reachable in the header.
- Destination + footer summary in an `aria-live="polite"` region so the running count is spoken.

---

## Business Rules

### BR-180: Pre-commit Review Is the Default Import Path
After a file is parsed, the system always presents the review surface before writing. Nothing is persisted until the user presses Import. This supersedes the immediate-write behavior of UC-031.

### BR-181: Default Selection
Everything is pre-selected except flagged duplicates. The "import everything I don't already have" case is one click.

### BR-182: Duplicate Detection (supersedes BR-061)
A bookmark whose normalized URL (BR-080) already exists in the **destination collection** is flagged as a duplicate. `Skip already in library` is **on** by default; duplicates are pre-deselected but can be hand-picked back in. BR-061 ("duplicates silently allowed, no de-dup") no longer holds for the reviewed path.

### BR-183: Destination and Folder Merge (extends BR-064)
Imports target a chosen collection (default: current) and an optional parent folder. Recreated folder paths **merge into an existing same-named folder at the same path** instead of creating duplicates like `Work (2)`. This replaces UC-031's root-only, always-create-new placement (BR-064).

### BR-184: Transient Selection
The parsed tree and selection live only for the duration of the review and are discarded on leave. Nothing about the selection is persisted.

### BR-185: Captures for Kept Bookmarks Only
The screenshot/capture pipeline is queued only for bookmarks actually committed — not for deselected or skipped-duplicate bookmarks.

### Inherited
BR-060 (Netscape HTML only), BR-062 (transactional commit), BR-065 (5 MB max) still apply.

---

## Open questions

- **Duplicate merge vs skip:** v1 only *skips* duplicates. Merging imported metadata into the existing bookmark is deferred.
- **utm stripping:** keep `normalizeUrl` as-is (recommended) or extend it — decide before building dedup so client and server stay aligned.
- **Future layouts:** Columns / Grouped are presentation-only follow-ups if a future import source produces genuinely huge, deeply-nested files.
