# Plan: Custom Auto-Tag Rules

## Context

UC-045 (Auto-Tag Bookmark by URL Pattern) shipped in MVP form with **built-in** rules only (`local`, `dev`, `uat`, `staging`, `test`, `qa`, `prod` — see `frontend/src/lib/auto-tag-rules.ts`). Built-in rules cover the obvious environment-subdomain pattern but cannot handle project-specific conventions (e.g. "any GitHub PR URL → `pr` + `github`", "anything under `wiki.acme.com` → `acme-wiki`").

This plan adds **user-defined per-collection rules** on top. Built-in rules remain and run first; custom rules add to (never override) the built-in suggestion list. Users author rules with **Java-flavoured regex against the full URL** plus **one or more target tag names**. A help popover with copy-paste examples teaches the syntax.

## Goals

- Per-collection rule list, owned by the collection owner; visible to all members of a shared collection but only editable by users with collection-edit rights.
- Rule = (1) regex against full URL + (2) one or more tag names + (3) optional description + (4) enabled flag.
- Suggestions integrate seamlessly into the existing "Suggested Tags" UI — user can't tell built-in apart from custom (except via the rule-management screen).
- Help popover with examples reachable from the create/edit form, in the spirit of "users have some software dev experience but want copy-paste examples".
- Rules evaluated **client-side** so suggestions are instant and offline-friendly. Server-side evaluation is not needed for v1.

## Non-goals

- Catch-all "if-then-else" workflow engine. One regex per rule.
- Server-side application of rules during bookmark import. Out of scope here; could be added later by reusing the same rule entity.
- Substitution / capture-group templating (e.g. `tag = $1`). Adds complexity without clear demand. Could be a v2.
- Conflict resolution beyond "first match wins per tag, dedupe by tag name".

## Data model

### New entity: `AutoTagRule`

`api/src/main/java/org/chainlink/api/bookmark/AutoTagRule.java`

| field          | type        | notes                                                                 |
|----------------|-------------|-----------------------------------------------------------------------|
| `id`           | UUID        | from `AbstractEntity`                                                 |
| `collection`   | `Collection` ManyToOne, NOT NULL                                                     |
| `pattern`      | `String`, NOT NULL, length 2000 — the regex source                                   |
| `tagNames`     | `String`, NOT NULL, length 2000 — comma-separated, normalised (lowercased, trimmed)   |
| `description`  | `String`, nullable, length `DbConst.DB_DEFAULT_MAX_LENGTH`                           |
| `enabled`      | `boolean`, NOT NULL, default `true`                                                  |
| `sortOrder`    | `int`, NOT NULL — supports manual ordering in the UI                                 |
| audit fields   | from `AbstractEntity`                                                                |

Why store `tagNames` as a comma-separated string and not as an FK list to `Tag`?
- Rules can reference tags **that don't yet exist** (the same "(new)" pattern we already use). An FK would force eager creation.
- Rule lifetime > tag lifetime: deleting a tag should not delete a rule that mentions it.
- Names match the existing client-side resolution path (`useTagSuggestions` already maps by lowercased name).
- Storage cost is negligible; querying isn't needed (rules are loaded as a small list per collection).

Constraints:
- No unique constraint on `(collection, pattern)` — duplicate intent is the user's call.
- Index: `ix_auto_tag_rule_collection_id` on `(collection_id, sort_order)`.

### Storage notes

- Pattern stored verbatim. Validation is **client-side preflight** (try `new RegExp(pattern)`) and **server-side enforcement** (`Pattern.compile(pattern)` in the service; throw `AppValidationException` on `PatternSyntaxException`).
- Tag names normalised to lowercase, trimmed, deduplicated, stored as `name1,name2`. Empty entries dropped. Limited to e.g. 8 tag names per rule (cap in validation).
- `sortOrder` — incremented by 10s per insert (`max + 10`), so reorder operations only touch one row most of the time. Drag-and-drop reorder reuses the same DnD plumbing as folders/bookmarks (see `frontend/src/composables/useDndMove.ts`).

### Flyway migration

`api/src/main/resources/db/migration/V9__Add_auto_tag_rule.sql`

```sql
CREATE TABLE auto_tag_rule (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    version         BIGINT       NOT NULL,
    timestamp_erstellt   TIMESTAMP NOT NULL,
    timestamp_mutiert    TIMESTAMP NOT NULL,
    user_erstellt        VARCHAR(255) NOT NULL,
    user_mutiert         VARCHAR(255) NOT NULL,
    collection_id   VARCHAR(36)  NOT NULL,
    pattern         VARCHAR(2000) NOT NULL,
    tag_names       VARCHAR(2000) NOT NULL,
    description     VARCHAR(255),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INTEGER      NOT NULL,
    CONSTRAINT fk_auto_tag_rule_collection
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE
);
CREATE INDEX ix_auto_tag_rule_collection_id ON auto_tag_rule(collection_id, sort_order);
```

(Adjust types to whatever audit-column conventions the existing migrations use — copy from `V8__Add_collection_favicon_allowlist.sql` and `V?__create_tag.sql`.)

## Backend

### Repository

`AutoTagRuleRepo` — Panache repository. One method: `findByCollectionOrderBySortOrder(ID<Collection>)`.

### Service

`AutoTagRuleService` (no auth checks — caller's job, per `CLAUDE.md`).

```
listForCollection(ID<Collection>): List<AutoTagRule>
create(AutoTagRuleSaveJson): AutoTagRule           // validates regex, assigns sortOrder = max+10
update(ID<AutoTagRule>, AutoTagRuleSaveJson): AutoTagRule
delete(ID<AutoTagRule>): void
reorder(ID<Collection>, List<ID<AutoTagRule>>): void   // bulk re-set sort_order
```

Pattern validation (server side):
```java
try { Pattern.compile(json.getPattern()); }
catch (PatternSyntaxException e) {
    throw new AppValidationException("autoTagRule.pattern.invalid", e.getMessage());
}
```

### Resource

`AutoTagRuleResource` at `/api/auto-tag-rules`. All endpoints call `authorizationService.requireCollectionAccess(collectionId)` before delegating.

| method | path                          | body / params                       | returns               |
|--------|-------------------------------|-------------------------------------|-----------------------|
| GET    | `/?collectionId=...`          | query                               | `AutoTagRuleListJson` |
| POST   | `/`                           | `AutoTagRuleSaveJson`               | `AutoTagRuleJson`     |
| PUT    | `/{ruleId}`                   | `AutoTagRuleSaveJson`               | `AutoTagRuleJson`     |
| DELETE | `/{ruleId}`                   | —                                   | 204                   |
| PUT    | `/reorder?collectionId=...`   | `OrderedIdsJson` (list of rule ids) | 204                   |

DTOs follow the project convention (`AutoTagRuleJson`, `AutoTagRuleSaveJson`, `AutoTagRuleListJson` — mirror `TagListJson`).

### CollectionInfo enrichment

Frontend already preloads `collectionInfo` (which contains tags, folders, etc.) on collection switch. Add `List<AutoTagRuleJson> autoTagRules` to `CollectionInfoJson` so the rule set is in memory for offline use and zero-latency suggestions. The existing GET endpoint that returns `CollectionInfoJson` will populate it.

### ArchUnit

No special concern — entity sits in the existing `bookmark` package (it's tightly coupled to tag resolution). If `chainlink-api` ArchUnit complains about location, move to a dedicated `org.chainlink.api.autotag` package.

## Frontend

### Generated client

Run `npm run generate-api` after the resource is in place. It produces `AutoTagRuleResourceApi`, `AutoTagRuleJson`, `AutoTagRuleSaveJson`, `AutoTagRuleListJson`.

### Store

`frontend/src/stores/autoTagRule.ts` — Pinia store.

```
const rules = computed<AutoTagRuleJson[]>(() =>
    collectionStore.collectionInfo?.autoTagRules ?? []
)

createRule(data: AutoTagRuleSaveJson): Promise<AutoTagRuleJson>
updateRule(id, data): Promise<AutoTagRuleJson>
deleteRule(id): Promise<void>
reorder(orderedIds: string[]): Promise<void>
```

Mirrors `frontend/src/stores/tag.ts`. Uses the same `patchAutoTagRules` helper pattern.

### Rule engine extension

Extend `frontend/src/lib/auto-tag-rules.ts`:

```ts
export interface CompiledCustomRule {
  id: string
  pattern: RegExp
  tagNames: string[]
}

export function compileCustomRules(rules: AutoTagRuleJson[]): CompiledCustomRule[]
export function suggestTagNames(rawUrl: string, customRules?: CompiledCustomRule[]): string[]
```

Compile-once-per-collection: `compileCustomRules` is called by `useTagSuggestions` and memoised on the input array reference. Invalid regex (shouldn't happen — server validates) is silently dropped with a `console.warn`.

Evaluation order (final ordered list, dedup by name):
1. Built-in `BUILT_IN_RULES`
2. Custom rules in `sortOrder`

This keeps existing built-in tests and behaviour untouched, and matches user expectation that built-in stage tags always come first.

`useTagSuggestions` (`frontend/src/composables/useTagSuggestions.ts`) — pull `autoTagRuleStore.rules` and pass through `compileCustomRules` into `suggestTagNames`. Recompute when rules change.

### Rule management UI

New view: `frontend/src/views/AutoTagRulesView.vue`, route `/collections/:id/auto-tag-rules`. Reachable from:
- The collection settings menu (next to favicon-allowlist editor).
- A small "Manage rules" link inside the "Suggested Tags" section of the bookmark dialog (only shown when section is visible).

Layout:
- Header: collection name + "Add rule" button + a "?" help icon (opens the help popover).
- A drag-handle list of rules. Each row:
  - drag handle
  - enabled toggle (switch)
  - pattern (monospaced)
  - "→" arrow
  - tag chips (rendered same way as in dialog)
  - description (muted)
  - edit / delete buttons
- Empty state: "No custom rules yet. Built-in rules still apply." + Add button + a quick example.

Add/Edit dialog: `AutoTagRuleDialog.vue` (used for both create and edit, like other dialogs). Fields:

| field        | UI                                                   |
|--------------|------------------------------------------------------|
| Pattern      | `<input type="text" class="font-mono">` + live tester (see below) |
| Tag names    | Tag-style chip input (comma / Enter to add a chip)   |
| Description  | optional textarea                                    |
| Enabled      | checkbox                                             |

**Live regex tester** beneath the pattern field:
- A small "Test URL" input. Below it: a green check / red x and the matched substring highlighted.
- Catches `SyntaxError` from `new RegExp(...)` and shows it inline.
- This is the most important UX: instant feedback while authoring.

Validation (zod):
- Pattern: required, max 2000, must compile (`.refine(s => { try{new RegExp(s)} catch{return false} return true })`).
- Tag names: 1–8, each 1–`DB_DEFAULT_MAX_LENGTH`, kebab-or-snake-case-friendly (i.e. don't reject anything `Tag.name` accepts).
- Description: max length matches DB column.

### Help popover

A new reusable component `frontend/src/components/ui/HelpPopoverCl.vue` built on Radix Vue's Popover primitive (already in the project). Trigger: a `<HelpCircle />` Lucide icon button. Content is slot-driven so other features (favicon allowlist already wants this too) can reuse it.

For auto-tag rules the popover content is a small markdown-styled panel with **copy-paste examples** — each example has a "Copy" button that copies the regex into the clipboard:

| Example                        | Pattern                              | Tags          |
|--------------------------------|--------------------------------------|---------------|
| GitHub PR URLs                 | `^https://github\.com/.+/pull/\d+`   | `pr, github`  |
| Confluence pages on `acme.atlassian.net` | `^https://acme\.atlassian\.net/wiki/`           | `acme-wiki`   |
| Anything under `*.k8s.acme.io` | `^https://[^/]+\.k8s\.acme\.io/`     | `k8s`         |
| Localhost on a non-default port| `^https?://localhost:(?!80\|443)\d+` | `local, port` |
| YouTube watch URLs             | `^https://(www\.)?youtube\.com/watch\?` | `video, youtube` |

Plus a short syntax note: "JavaScript regex syntax (the same flavour as in your IDE's `// /…/` literal). Anchor with `^` to match from the start of the URL. Wrap literal dots as `\.`. Use `(?:…)` for non-capturing groups." Link to MDN regex reference.

The **same popover is reachable** from the suggestions section in the bookmark dialog (next to the "Suggested Tags" label), so a user encountering the feature for the first time can learn it without navigating away.

### Sharing model & permissions

- Rules belong to the collection. `AuthorizationService.requireCollectionEditAccess(collectionId)` gates create/update/delete (introduce that check method if it doesn't exist yet — current `requireCollectionAccess` likely treats viewers as having access).
- Viewers see suggestions but cannot edit rules (UI hides the manage button for them).

### i18n

New keys under `bookmark` and a new `autoTagRule` block. (en + de.) Examples:

- `autoTagRule.title`, `autoTagRule.add`, `autoTagRule.edit`, `autoTagRule.delete`,
- `autoTagRule.pattern`, `autoTagRule.patternHelp`, `autoTagRule.patternInvalid`,
- `autoTagRule.tagNames`, `autoTagRule.tagNamesHelp`,
- `autoTagRule.description`, `autoTagRule.enabled`,
- `autoTagRule.testUrl`, `autoTagRule.testMatched`, `autoTagRule.testNotMatched`,
- `autoTagRule.helpExamples`, `autoTagRule.copyExample`,
- `autoTagRule.empty`,
- `bookmark.manageRules`.

## Tests

### Backend

- Unit: `AutoTagRuleServiceTest` — pattern validation rejects bad regex, sortOrder assignment, normalisation of `tagNames`, reorder bulk update.
- Integration: `AutoTagRuleResourceITest` — full CRUD with `@TestSecurity`, plus a 403 case for a user who lacks edit access on the collection, plus `CollectionInfoJson` includes the rules.

### Frontend

- Unit (`auto-tag-rules.spec.ts` extension):
  - `compileCustomRules` skips invalid patterns.
  - `suggestTagNames` with custom rules: built-in first, then custom, dedup by name, ordering preserved.
  - Empty `tagNames` rule contributes nothing.
- Component test for `AutoTagRuleDialog.vue` — test that the live tester reflects matches and validation errors.
- E2E (`frontend/e2e/auto-tag-rules.spec.ts`, with `afterAll` cleanup mirroring `auto-tag.spec.ts`):
  1. Open rule manager from a bookmark dialog, add a rule (`^https://github\.com/.+/pull/\d+` → `pr, github`), save.
  2. Open Create Bookmark, type `https://github.com/foo/bar/pull/42`, see `pr` + `github` in suggestions, accept, submit, verify both tags applied.
  3. Disable the rule via toggle, retry create — suggestions no longer include those tags.
  4. Delete the rule. afterAll: delete any AutoTag-* bookmarks AND any custom rule that we created AND `pr`/`github`/`AutoTagRules-*` tags introduced by the spec.

## Migration / rollout

- Single Flyway migration; no data migration needed (built-in rules stay in code).
- Feature is purely additive to UC-045. Existing bookmarks are unaffected.
- Document in `docs/use_cases/UC-045-auto-tag-bookmark-by-url-pattern.md` under a new "Custom rules (BR-076..)" section, and add a follow-up UC-053 if you want to track this as its own use case.

## Open questions to confirm before coding

1. **Edit rights**: does the project already model "owner vs viewer" within a shared collection, and is there a `requireCollectionEditAccess` (or similar) helper? If only one access level exists, the rule manager is editable by anyone with collection access for v1.
2. **Capture-group templating** (`$1` in tag name): explicitly skip for v1?
3. **Built-in rule visibility**: should built-in rules show up (read-only) at the top of the rule manager so users understand the precedence and can disable individual built-ins per collection? My recommendation: yes, but as a separate v1.5 — would need a `disabledBuiltIns: Set<String>` column on `Collection`.
4. **Per-rule color**: should a custom rule be able to specify the color of newly-created tags? Easy add (column in `auto_tag_rule`); user-friendly. Recommend yes.

## Critical files (quick index)

- `api/src/main/java/org/chainlink/api/bookmark/Tag.java` — entity template to copy
- `api/src/main/java/org/chainlink/api/collection/Collection.java` — FK target
- `api/src/main/java/org/chainlink/api/shared/auth/AuthorizationService.java` — add `requireCollectionEditAccess` if missing
- `api/src/main/resources/db/migration/V8__Add_collection_favicon_allowlist.sql` — migration style reference
- `api/src/main/java/org/chainlink/api/collection/CollectionInfoJson.java` (or wherever it lives) — extend with `autoTagRules`
- `frontend/src/lib/auto-tag-rules.ts` — extend with custom rule pipeline
- `frontend/src/composables/useTagSuggestions.ts` — wire in custom rules
- `frontend/src/components/bookmark/CreateBookmarkDialog.vue` & `EditBookmarkDialog.vue` — add help-popover trigger and "Manage rules" link
- `frontend/src/stores/tag.ts` — pattern reference for new `autoTagRule.ts` store
- `frontend/src/components/collection/EditCollectionDialog.vue` — sibling of where the Manage Rules entrypoint lives
- `frontend/src/composables/useDndMove.ts` — reuse for drag-reorder
- `frontend/src/i18n/locales/{en,de}.json` — add new keys
- `frontend/e2e/auto-tag.spec.ts` — pattern reference for new e2e spec including `afterAll` cleanup

## Verification

1. `cd api && ./mvnw verify` — migration applies, repo+service+resource tests green.
2. `cd frontend && npm run generate-api && npm run type-check && npx vitest run`.
3. `cd frontend && npm run dev`, manual:
   - Add a custom rule via the new manager. Live tester shows match.
   - Create a bookmark whose URL hits the rule → see custom suggestion alongside built-ins.
   - Disable rule → suggestion disappears.
   - Help popover opens, examples copy to clipboard.
4. `npx playwright test --project=chromium frontend/e2e/auto-tag-rules.spec.ts`.
