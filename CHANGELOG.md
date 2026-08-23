# CHANGELOG

## [1.0.0] - 2025-01-01

### Added - Step 1: Domain Model

- **Domain Entities**:
  - `Bookmark` entity with title, url, notes, folder (ManyToOne), and tags (ManyToMany) relations
  - `Tag` entity with name field and ManyToMany relation to Bookmark
  - `Folder` entity with name field and OneToMany relation to Bookmark
  - All entities extend `AbstractEntity` for id, version, timestamps, and auditing fields

- **Dependencies**:
  - Added `htmx.org` (v1.9.12) to pom.xml for htmx UI interactions

- **Configuration**:
  - Configured Web Bundler to install htmx in application.properties

### Files Changed

- `src/main/java/org/linkweave/domain/Bookmark.java` - New file
- `src/main/java/org/linkweave/domain/Tag.java` - New file
- `src/main/java/org/linkweave/domain/Folder.java` - New file
- `pom.xml` - Added htmx.org dependency
- `src/main/resources/application.properties` - Added web-bundler configuration
- `CHANGELOG.md` - Created

### Technical Notes

- Entities use Lombok `@SuperBuilder` and `@NoArgsConstructor`
- Entities use Jakarta Bean Validation annotations
- Folder and Tag have cascading relations to Bookmarks
- htmx will be bundled and served via Quarkus Web Bundler

## [1.0.1] - 2026-02-05

### Added - Step 2: Repository Layer

- **Repositories**:
  - `TagRepo` with findAll(), findByName(), and getByName() methods
  - `BookmarkRepo` with findAll(), findByFolder(), findByFolderId(), findByTag(), findByTagId(), and searchByTitle() methods
  - Updated `FolderRepo` with findAll() method

- **Services**:
  - `TagService` with createTag(), getTag(), getTagByName(), getAllTags(), updateTag(), and removeTag() methods
  - `BookmarkService` with createBookmark(), getBookmark(), getAllBookmarks(), getBookmarksByFolder(), getBookmarksByFolderId(), getBookmarksByTag(), getBookmarksByTagId(), searchBookmarks(), updateBookmark(), and removeBookmark() methods
  - Updated `FolderService` with getAllFolders() method

### Added - Step 3: REST Endpoints

- **BookmarkResource** (`/bookmarks`):
  - `GET /bookmarks` - Main bookmark list page with optional folder/tag filtering
  - `GET /bookmarks/new` - Bookmark creation form
  - `GET /bookmarks/{id}` - Bookmark edit form
  - `POST /bookmarks` - Create bookmark endpoint

### Added - Step 4: Qute Templates

- **Main Templates**:
  - `bookmarks.qute.html` - Main page with sidebar navigation and bookmark list
  - `login.html` - Login page with form-based authentication
  - `bookmark-form.html` - Bookmark creation/edit form with htmx integration

- **Template Fragments**:
  - `bookmark-list.html` - Bookmark list display fragment for htmx updates

### Added - Step 5: Frontend Integration

- **JavaScript**:
  - Updated `app.js` with htmx integration
  - Service worker registration for offline caching
  - Online/offline status detection and indicator
  - htmx event listeners for request configuration and error handling
  - showBookmarkForm() function for dynamic form loading

- **Service Worker** (`sw.js`):
  - Network-first caching strategy for bookmark pages
  - Cache-first fallback for offline scenarios
  - Blocks POST/PUT/DELETE requests when offline
  - Cache invalidation on activation
  - Cache name: 'bookmark-manager-v1'

- **Styles** (`app.scss`):
  - Complete dark theme matching UI design specification
  - Sidebar navigation styles with folders and tags
  - Bookmark list with card-style items
  - Form styles with dark background colors
  - Login page styles
  - Tag color coding (blue, green, orange, purple, pink)
  - Offline indicator styles
  - Responsive design for mobile devices

### Files Changed

- `src/main/java/org/linkweave/api/bookmark/TagRepo.java` - New file
- `src/main/java/org/linkweave/api/bookmark/BookmarkRepo.java` - New file
- `src/main/java/org/linkweave/api/bookmark/TagService.java` - New file
- `src/main/java/org/linkweave/api/bookmark/BookmarkService.java` - New file
- `src/main/java/org/linkweave/api/bookmark/BookmarkResource.java` - New file
- `src/main/java/org/linkweave/api/bookmark/folder/FolderService.java` - Updated with getAllFolders()
- `src/main/resources/templates/bookmarks.qute.html` - New file
- `src/main/resources/templates/login.html` - New file
- `src/main/resources/templates/bookmark-form.html` - New file
- `src/main/resources/templates/bookmark-list.html` - New file
- `src/main/resources/web/app.js` - Updated with htmx and service worker
- `src/main/resources/web/sw.js` - New file
- `src/main/resources/web/app.scss` - Complete dark theme implementation

### Technical Notes

- Repositories use QueryDSL for type-safe database queries
- Services use @RequiredArgsConstructor for dependency injection
- Resources use JAX-RS annotations and return Qute TemplateInstance
- htmx enables partial page updates without full page reloads
- Service worker provides read-only offline access as per specification
- Dark theme uses color palette: #1e1e1e (bg), #252525 (sidebar/cards), #4695EB (primary)
- Tag colors: #4695EB (blue), #22c55e (green), #f97316 (orange), #a855f7 (purple), #ec4899 (pink)
- Offline indicator displays red "Offline – read-only mode" message

### Pending Implementation

- Session-based authentication configuration
- Login/logout endpoints
- Tag REST endpoints
- Folder REST endpoints
- Bookmark update and delete endpoints
- Cache invalidation on logout
- Additional folder management features
- Tag management UI

## [1.0.2] - 2026-04-13

### Added - Step 6: Architecture & Quality

- **Use Cases**:
  - `UC-035`: Add ArchUnit Tests - Specification for automated architectural and coding standard enforcement based on the `esc` project rules.

- **Requirements**:
  - `FR-039`: Functional requirement for automated ArchUnit tests.
  - `NFR-007`: Non-functional requirement for architectural enforcement.

### Files Changed

- `docs/use_cases/UC-035-add-archunit-tests.md` - New use case specification.
- `docs/use_cases.puml` - Updated with new section and Developer actor.
- `docs/requirements.md` - Added FR-039 and NFR-007.

### Added - Step 7: Error Handling Specifications

- **Requirements**:
  - `FR-040`: Functional requirement for user feedback on failed operations.
  - `NFR-008`: Non-functional requirement for robust, centralized error handling and visibility.

### Files Changed

- `docs/requirements.md` - Added FR-040 and NFR-008, updated document date.
- `CHANGELOG.md` - Added Step 7.

### Added - Step 8: Session Security and Data Isolation

- **Requirements**:
  - `FR-041`: Functional requirement for complete session data cleanup.
  - `NFR-009`: Non-functional requirement for state isolation between user sessions.

### Files Changed

- `docs/requirements.md` - Added FR-041 and NFR-009.
- `CHANGELOG.md` - Added Step 8.

### Added - Step 9: E2E Testing Pipeline Requirement

- **Requirements**:
  - `NFR-010`: Mandated Playwright E2E tests for Chrome to run in the CI pipeline for every change.
- **Vision**:
  - Updated quality goals to include continuous integration of E2E tests.

### Files Changed

- `docs/requirements.md` - Added NFR-010.
- `docs/vision.md` - Updated Quality Goals.
- `CHANGELOG.md` - Added Step 9.

## [1.0.3] - 2026-08-23

### Updated - Step 10: Requirements Status Sync

Verified implemented features against the codebase and flipped their requirement statuses from `Open` to `Done`:

- **FR-078** Multi-Select Bookmarks, **FR-079** Batch Tag Application, **FR-080** Batch Move and Delete
  (PRs #72 / #86: selection model, `BatchActionBar`, `/bookmarks/batch-move|batch-delete|batch-tag` endpoints).
- **NFR-018** Batch Operation Atomicity (batch service methods execute in one transaction; rollback hardening).
- **C-017** Batch Size Limit (`@Size(max = 500)` on batch DTOs → HTTP 400).
- **FR-094** Review and Select Bookmarks Before Import (`/import/preview` + `/import/commit`, `ImportReviewView.vue`).
- **FR-095** Auto-Tag Bookmarks with a Local LLM, **FR-096** LLM Feature Flag (`linkweave.autotag.llm.enabled`),
  **FR-097** Pluggable LLM Provider (`linkweave.autotag.provider`: `ollama` | `openai`) (PR #96).
- **FR-083** Manage API Keys, **FR-084** Authenticate via API Key, **NFR-020** API Key Storage Security,
  **NFR-021** API Key Rotation Limit, **C-018** Custom `HttpAuthenticationMechanism`
  (`api/.../auth/apikey/`: `ApiKey` entity, SHA-256 hash storage, reveal-once UI, `MAX_ACTIVE_KEYS = 10`).

Not changed (still genuinely open): FR-081 Bookmark Notes, FR-087 Extension Property Support,
FR-092 Mobile Share Sheet (spec-only commit, no implementation yet). The CLI (FR-085/086),
NFR-028 WAL feasibility report, and FR-098–FR-102 landed upstream in the meantime and are
reflected here via the rebase onto origin/main.

### Files Changed

- `docs/requirements.md` - Status updates for FR-078, FR-079, FR-080, FR-083, FR-084, FR-094, FR-095, FR-096, FR-097, NFR-018, NFR-020, NFR-021, C-017, C-018; document date refreshed.
- `docs/use_cases/UC-096-review-and-select-bookmarks-before-import.md` - Status Proposed → Implemented.
- `docs/use_cases/UC-097-autotag-bookmark-with-local-llm.md` - Status Draft → Implemented.
- `CHANGELOG.md` - Added Step 10.
