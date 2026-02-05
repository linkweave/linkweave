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

- `src/main/java/org/chainlink/domain/Bookmark.java` - New file
- `src/main/java/org/chainlink/domain/Tag.java` - New file
- `src/main/java/org/chainlink/domain/Folder.java` - New file
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

- `src/main/java/org/chainlink/api/bookmark/TagRepo.java` - New file
- `src/main/java/org/chainlink/api/bookmark/BookmarkRepo.java` - New file
- `src/main/java/org/chainlink/api/bookmark/TagService.java` - New file
- `src/main/java/org/chainlink/api/bookmark/BookmarkService.java` - New file
- `src/main/java/org/chainlink/api/bookmark/BookmarkResource.java` - New file
- `src/main/java/org/chainlink/api/bookmark/folder/FolderService.java` - Updated with getAllFolders()
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
