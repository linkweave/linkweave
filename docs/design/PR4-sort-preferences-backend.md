# PR 4 — Sort Preferences: Backend

**Goal.** Persist a per-(user, collection) sort preference. Surfaces through the existing `GET/PUT /collections/{id}/settings` endpoint plus one small reset endpoint; no new resource needed.

**Depends on.** Nothing on the frontend. Can ship before PR2/PR5.
**Unblocks.** PR 5 (frontend wiring).

**Pre-existing facts that shape this PR:**

1. `CollectionAccess.settings` (TEXT JSON column) exists since V11 — already serving `layout`.
2. `CollectionSettingsJson` + `CollectionSettingsService` + `GET/PUT /collections/{id}/settings` exist.
3. `Bookmark.click_count` and `Bookmark.last_clicked_at` exist on the bookmark itself (aggregate, **not per-user**). In shared collections, "Last clicked" and "Click count" reflect *all collaborators' clicks*. We document this and move on — adding a per-user click table is a much larger change for marginal value, and FR-082 doesn't require it.

So this PR is *small*:

- Extend `CollectionSettingsJson` to carry sort preference. Use existing `GET/PUT /collections/{id}/settings`.
- Add a `DELETE /collections/{id}/settings/sort` to clear the sort fields while keeping the layout preference.

> **Scope note — per-user default sort dropped.** An earlier draft of this PR added a per-user default sort (with a `User` migration, `UserSettingsResource`, and an extension to `/auth/me`). It was cut: per-collection preferences are sticky and most users only have a handful of collections, so the default would rarely fire while doubling the API surface and adding a discoverability problem (users would have to find the Settings dialog). Re-introduce later if user feedback demands it.

---

## Spec docs first

Before any code, update `docs/requirements.md` and the use cases. This is non-negotiable per AGENTS.md ("Specifications are the source of truth").

- **FR-082** stays a single requirement, retitled "Sort Preferences (per collection)" with explicit text that collections without an explicit preference fall back to the system default (creation date descending). Status: Open → In Progress.
- **Acknowledge in FR-082** that "Last clicked" and "Click count" reflect shared aggregate click data, not per-user activity. One sentence on the FR is enough — but it must be there so future readers don't assume per-user click tracking is implied.
- **UC-076 (Sort Bookmarks)** — keep BR-108 ("default sort = creation date descending") and add the shared-clicks note. The "Change Default Sort" alternative flow is removed (no longer in scope).

No new use case file is needed.

---

## Sort field enum

Define an enum in `org.linkweave.api.collection.SortField`:

```java
package org.linkweave.api.collection;

public enum SortField {
    TITLE,
    DATE_ADDED,
    LAST_CLICKED,
    CLICK_COUNT
}
```

And:

```java
package org.linkweave.api.collection;

public enum SortDirection {
    ASC,
    DESC
}
```

Both go in the `collection` package (settings live there, so the types live there). String-serialised in JSON. No DB columns — they're carried in the existing TEXT settings blob.

---

## Files touched

### 1. `CollectionSettingsJson.java` — extend

```diff
 @Value
 @NonFinal
 @JaxDTO
 @NoArgsConstructor(force = true)
 @AllArgsConstructor
 public class CollectionSettingsJson {

     @Nullable String layout;
+    @Nullable SortField sortField;
+    @Nullable SortDirection sortDirection;
 }
```

All three fields remain `@Nullable` — `null` means "fall back to system default (`DATE_ADDED, DESC`)". The frontend uses null to express "I have not set a preference here."

### 2. `CollectionSettingsService.updateSettings` — merge new fields

```diff
     @Transactional
     @NonNull
     public CollectionSettingsJson updateSettings(
         @NonNull ID<Collection> collectionId,
         @NonNull CollectionSettingsJson patch
     ) {
         var userId = currentUserService.currentUser().getId();
         var access = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
         var current = parse(access.getSettings());
         var merged = new CollectionSettingsJson(
-            patch.getLayout() != null ? patch.getLayout() : current.getLayout()
+            patch.getLayout() != null ? patch.getLayout() : current.getLayout(),
+            patch.getSortField() != null ? patch.getSortField() : current.getSortField(),
+            patch.getSortDirection() != null ? patch.getSortDirection() : current.getSortDirection()
         );
         access.setSettings(serialize(merged));
         return merged;
     }
```

The PATCH-style merge already in place (`patch != null ? patch : current`) is exactly what we want — frontend can update just `sortField`, just `sortDirection`, or both.

**Reset:** to clear a per-collection preference and fall back to the system default, the frontend sends a request whose intent is "blank these fields out" — but the current merge code keeps `current` when `patch` is null, so a `null`-in-body approach doesn't work. We add an explicit reset endpoint instead: `DELETE /collections/{id}/settings/sort`. One small endpoint + service method:

```java
// In CollectionResource.java
@DELETE
@Path("{id}/settings/sort")
@Authenticated
public void resetSortPreference(@PathParam("id") ID<Collection> id) {
    authorizationService.requireCollectionAccess(id);
    collectionSettingsService.resetSortPreference(id);
}

// In CollectionSettingsService.java
@Transactional
public void resetSortPreference(@NonNull ID<Collection> collectionId) {
    var userId = currentUserService.currentUser().getId();
    var access = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
    var current = parse(access.getSettings());
    var reset = new CollectionSettingsJson(current.getLayout(), null, null);
    access.setSettings(serialize(reset));
}
```

### 3. `CollectionInfoJson` (optional)

`CollectionInfoJson` does **not currently include settings**. Frontend calls `GET /collections/{id}/settings` separately. That's fine; leave as-is. Or, for a one-request hydrate, add `settings: CollectionSettingsJson` to `CollectionInfoJson` and populate it in `CollectionInfoMapper` (the user is always `currentUser` in that endpoint). Up to you — non-blocking for this PR.

---

## Tests

Follow the project's `should*` naming, integration tests with `@TestSecurity` for the auth context.

### `CollectionSettingsResourceITest` (integration) — add cases

- `shouldRoundTripSortPreferenceViaSettingsEndpoint`
- `shouldReturn403WhenAccessingAnotherUsersCollectionSettings`
- `shouldResetSortPreferenceViaDeleteEndpoint_keepingLayout`

### ArchUnit

Existing rules should cover the new endpoint (JaxResource stereotype, `@Authenticated` per-method, naming, layering).

---

## API contract summary (for the OpenAPI clients to regenerate)

| Method | Path | Body | Returns | Notes |
|---|---|---|---|---|
| `GET` | `/api/collections/{id}/settings` | — | `CollectionSettingsJson` | unchanged endpoint; now carries sort fields |
| `PUT` | `/api/collections/{id}/settings` | `CollectionSettingsJson` (partial) | merged `CollectionSettingsJson` | unchanged endpoint; merges new fields |
| `DELETE` | `/api/collections/{id}/settings/sort` | — | 204 | **new**, clears sort fields only |

---

## Acceptance checklist

- [ ] FR-082 updated in `docs/requirements.md` (per-collection scope only, shared-clicks note included).
- [ ] UC-076 retains BR-108 (system default fallback) and carries the shared-clicks note.
- [ ] `SortField` and `SortDirection` enums in `org.linkweave.api.collection`.
- [ ] `CollectionSettingsJson` carries `sortField` + `sortDirection` (nullable).
- [ ] PUT `/collections/{id}/settings` merges new fields; existing layout preference unaffected.
- [ ] DELETE `/collections/{id}/settings/sort` clears sort fields, keeps layout.
- [ ] Authorization: `/collections/{id}/settings*` calls `requireCollectionAccess` (already does for the existing endpoints; verify the new DELETE).
- [ ] New integration tests pass.
- [ ] `cd api && ./mvnw verify` clean.

---

## Out of scope

- Server-side sorting. Bookmarks are returned in `created_at desc` order today and the frontend sorts in-memory. Don't change this in PR4.
- Per-user click tracking. Aggregate is fine for now.
- Per-user default sort. Cut from this PR (see scope note at the top); reconsider if user feedback shows the per-collection-only model is painful.
