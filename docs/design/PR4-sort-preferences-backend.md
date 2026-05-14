# PR 4 — Sort Preferences: Backend

**Goal.** Persist a per-(user, collection) sort preference and a per-user default sort. Both surface through DTOs the frontend already polls; no new endpoints needed for the per-collection case.

**Depends on.** Nothing on the frontend. Can ship before PR2/PR5.
**Unblocks.** PR 5 (frontend wiring).
**Pre-existing facts that shape this PR:**

1. `CollectionAccess.settings` (TEXT JSON column) exists since V11 — already serving `layout`.
2. `CollectionSettingsJson` + `CollectionSettingsService` + `GET/PUT /collections/{id}/settings` exist.
3. `Bookmark.click_count` and `Bookmark.last_clicked_at` exist on the bookmark itself (aggregate, **not per-user**). This means in shared collections, "Last clicked" and "Click count" reflect *all collaborators' clicks*. We document this and move on — adding a per-user click table is a much larger change for marginal value, and FR-082 doesn't require it.

So this PR is *small*:

- Extend `CollectionSettingsJson` to carry sort preference. Use existing `GET/PUT /collections/{id}/settings`.
- Add a per-user default sort. Either as columns on `User`, or as a parallel `User.settings` JSON column. We recommend **two columns on `User`** — typed, indexable, simpler than a second JSON blob. Add a tiny `UserSettingsResource` + `UserSettingsService` exposing `GET/PUT /users/me/settings`.

---

## Spec docs first

Before any code, update `docs/requirements.md` and add use cases. This is non-negotiable per AGENTS.md ("Specifications are the source of truth"). Split FR-082 into:

| ID | Title | Notes |
|---|---|---|
| FR-082 | Sort Preferences (per collection) | The per-(user, collection) preference. Status: Open → In Progress. |
| FR-082a | Default Sort for New Collections | The per-user default. New FR. |

Remove the part of FR-082 that says "A default sort for new collections must be configurable in settings" — that's FR-082a now.

Add corresponding use cases under `docs/use_cases/`:

- `uc-set-collection-sort.md` — User changes sort field/direction in a collection; preference persists across logins and devices.
- `uc-set-default-sort.md` — User sets a default; future collections (and any collection without an explicit preference) use it.

**Acknowledge in the spec** that "Last clicked" and "Click count" reflect shared aggregate click data, not per-user activity. One sentence under FR-082 is enough — but it must be there so future readers don't assume per-user click tracking is implied.

---

## Sort field enum

Define an enum in `org.chainlink.api.collection.SortField`:

```java
package org.chainlink.api.collection;

public enum SortField {
    TITLE,
    DATE_ADDED,
    LAST_CLICKED,
    CLICK_COUNT
}
```

And:

```java
package org.chainlink.api.collection;

public enum SortDirection {
    ASC,
    DESC
}
```

Both go in the `collection` package (settings live there, so the types live there). String-serialised in JSON. No DB columns yet — they're carried in the existing TEXT settings blob.

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

All three fields remain `@Nullable` — `null` means "fall back to user default; if that is also null, system default". The frontend uses null to express "I have not set a preference here."

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

**Reset:** to clear a per-collection preference and fall back to the user default, the frontend sends `{"sortField": null, "sortDirection": null}` — but the current merge code keeps `current` when `patch` is null, so that doesn't work. Either:

- (a) Change the merge to "every field in the request body is authoritative, even if null." This requires distinguishing "absent from JSON" from "explicit null", which Jackson does poorly with `@Value` records.
- (b) Add an explicit reset endpoint: `DELETE /collections/{id}/settings/sort`.

**Pick (b)** — it's cleaner. One small endpoint + service method:

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

### 3. User-level default sort

#### 3a. Migration `V13__Add_user_default_sort.sql`

```sql
ALTER TABLE User ADD COLUMN default_sort_field VARCHAR(20);
ALTER TABLE User ADD COLUMN default_sort_direction VARCHAR(10);

ALTER TABLE User_AUD ADD COLUMN default_sort_field VARCHAR(20);
ALTER TABLE User_AUD ADD COLUMN default_sort_direction VARCHAR(10);
```

Both columns nullable. Null = system default (`DATE_ADDED, DESC`). String storage (`@Enumerated(EnumType.STRING)`) matches the codebase convention.

#### 3b. Update `User.java` entity

```diff
+import jakarta.persistence.EnumType;
+import jakarta.persistence.Enumerated;
+import org.chainlink.api.collection.SortDirection;
+import org.chainlink.api.collection.SortField;
+import org.chainlink.infrastructure.db.DbConst;

 public class User extends AbstractEntity<User> {
     ...
+    @Nullable
+    @Enumerated(EnumType.STRING)
+    @Column(name = "default_sort_field", length = DbConst.DB_ENUM_LENGTH)
+    private SortField defaultSortField;
+
+    @Nullable
+    @Enumerated(EnumType.STRING)
+    @Column(name = "default_sort_direction", length = DbConst.DB_ENUM_LENGTH)
+    private SortDirection defaultSortDirection;
 }
```

(Look up the actual `User.java` location — it's in `api/src/main/java/org/chainlink/api/shared/user/` based on existing imports.)

#### 3c. New DTO `UserSettingsJson.java` (same package as User)

```java
package org.chainlink.api.shared.user;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.chainlink.api.collection.SortDirection;
import org.chainlink.api.collection.SortField;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.Nullable;

@Value
@NonFinal
@JaxDTO
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class UserSettingsJson {
    @Nullable SortField defaultSortField;
    @Nullable SortDirection defaultSortDirection;
}
```

#### 3d. New service `UserSettingsService.java`

```java
package org.chainlink.api.shared.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final CurrentUserService currentUserService;
    private final UserRepo userRepo;  // verify exact name; might be UserRepository

    @NonNull
    public UserSettingsJson getSettings() {
        var user = currentUserService.currentUser();
        return new UserSettingsJson(user.getDefaultSortField(), user.getDefaultSortDirection());
    }

    @Transactional
    @NonNull
    public UserSettingsJson updateSettings(@NonNull UserSettingsJson patch) {
        var user = currentUserService.currentUser();
        // Merge: null in patch means "leave alone".
        if (patch.getDefaultSortField() != null) {
            user.setDefaultSortField(patch.getDefaultSortField());
        }
        if (patch.getDefaultSortDirection() != null) {
            user.setDefaultSortDirection(patch.getDefaultSortDirection());
        }
        return new UserSettingsJson(user.getDefaultSortField(), user.getDefaultSortDirection());
    }

    @Transactional
    public void resetDefaultSort() {
        var user = currentUserService.currentUser();
        user.setDefaultSortField(null);
        user.setDefaultSortDirection(null);
    }
}
```

#### 3e. New resource `UserSettingsResource.java`

```java
package org.chainlink.api.shared.user;

import java.time.temporal.ChronoUnit;

import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 60, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/users/me/settings")
public class UserSettingsResource {

    private final UserSettingsService userSettingsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    public UserSettingsJson getSettings() {
        return userSettingsService.getSettings();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    public UserSettingsJson updateSettings(@Valid @NonNull UserSettingsJson json) {
        return userSettingsService.updateSettings(json);
    }

    @DELETE
    @Path("/sort")
    public void resetDefaultSort() {
        userSettingsService.resetDefaultSort();
    }
}
```

No authorization-service call needed — these endpoints are scoped to `/users/me`, the current user is always the subject.

### 4. Expose user default on `/auth/me` (optional but recommended)

Frontend already calls `/auth/me` on app boot. Folding `defaultSortField`/`defaultSortDirection` into `UserInfoJson` saves a second round-trip:

```diff
 // UserInfoJson.java
 public class UserInfoJson {
     ...
+    @Nullable SortField defaultSortField;
+    @Nullable SortDirection defaultSortDirection;
 }
```

And populate it in whatever maps the User to UserInfoJson. If the frontend can hydrate both the per-collection preference (already inside `CollectionInfoJson` via settings) and the user default in the two requests it already makes, no new requests are needed at runtime.

Note: `CollectionInfoJson` does **not currently include settings**. Check `CollectionInfoMapper` — `getCollectionInfoById` does not return the user's `CollectionAccess.settings`. Frontend currently calls `GET /collections/{id}/settings` separately. That's fine; leave as-is. Or, for a one-request hydrate, add `settings: CollectionSettingsJson` to `CollectionInfoJson` and populate it in the mapper (the user is always `currentUser` in that endpoint). Up to you — non-blocking for this PR.

---

## Tests

Follow the project's `should*` naming, integration tests with `@TestSecurity` for the auth context.

### `CollectionSettingsServiceTest` (unit) — add cases

- `shouldStoreSortFieldAndDirectionInSettingsJson`
- `shouldMergePartialSortUpdatesWithExistingLayout`
- `shouldClearOnlySortPreferenceOnReset_keepingLayout`
- `shouldReturnNullsWhenNoPreferenceSet`

### `CollectionSettingsResourceITest` (integration) — add cases

- `shouldRoundTripSortPreferenceViaSettingsEndpoint`
- `shouldReturn403WhenAccessingAnotherUsersCollectionSettings`
- `shouldResetSortPreferenceViaDeleteEndpoint`

### `UserSettingsServiceTest` (new, unit)

- `shouldStoreUserDefaultSortField`
- `shouldUpdateOnlyDirectionAndKeepFieldUnchanged`
- `shouldResetDefaultSortToNulls`

### `UserSettingsResourceITest` (new, integration)

- `shouldRequireAuthentication`
- `shouldReturnNullsWhenUserHasNoDefault`
- `shouldRoundTripDefaultSortViaPut`
- `shouldResetDefaultSortViaDelete`

### ArchUnit

Existing rules should cover the new classes (JaxResource stereotype, naming, layering). If any new test class is needed, mirror existing patterns under `api/src/test/`.

---

## API contract summary (for the OpenAPI clients to regenerate)

| Method | Path | Body | Returns | Notes |
|---|---|---|---|---|
| `GET` | `/api/collections/{id}/settings` | — | `CollectionSettingsJson` | unchanged endpoint; now carries sort fields |
| `PUT` | `/api/collections/{id}/settings` | `CollectionSettingsJson` (partial) | merged `CollectionSettingsJson` | unchanged endpoint; merges new fields |
| `DELETE` | `/api/collections/{id}/settings/sort` | — | 204 | **new**, clears sort fields only |
| `GET` | `/api/users/me/settings` | — | `UserSettingsJson` | **new** |
| `PUT` | `/api/users/me/settings` | `UserSettingsJson` (partial) | merged `UserSettingsJson` | **new** |
| `DELETE` | `/api/users/me/settings/sort` | — | 204 | **new** |
| `GET` | `/api/auth/me` | — | `UserInfoJson` *(with default sort)* | extended payload, optional |

---

## Acceptance checklist

- [ ] FR-082 / FR-082a split in `docs/requirements.md`; use cases written.
- [ ] `SortField` and `SortDirection` enums in `org.chainlink.api.collection`.
- [ ] `CollectionSettingsJson` carries `sortField` + `sortDirection` (nullable).
- [ ] PUT `/collections/{id}/settings` merges new fields; existing layout preference unaffected.
- [ ] DELETE `/collections/{id}/settings/sort` clears sort fields, keeps layout.
- [ ] Migration V13 adds `default_sort_field`, `default_sort_direction` on `User` (and `User_AUD`).
- [ ] `User.java` entity has the two new nullable enum-typed columns.
- [ ] `UserSettingsResource` + `UserSettingsService` + `UserSettingsJson` in place.
- [ ] `UserInfoJson` (`/auth/me`) returns the user's default sort fields.
- [ ] Authorization: `/collections/{id}/settings*` calls `requireCollectionAccess` (already does for the existing endpoints; verify the new DELETE).
- [ ] All new unit + integration tests pass.
- [ ] `cd api && ./mvnw verify` clean.
- [ ] One-line note in FR-082 acknowledging that "Last clicked" / "Click count" reflect *aggregate* clicks across all collection members.

---

## Out of scope

- Server-side sorting. Bookmarks are returned in `created_at desc` order today and the frontend sorts in-memory. Don't change this in PR4.
- Per-user click tracking. Aggregate is fine for now.
- Resetting the user default by sending nulls in PUT — use the DELETE endpoint instead.
