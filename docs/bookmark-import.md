# UC-031: Import Browser Bookmarks — Implementation Plan

**Related:** FR-031, [UC-031 Use Case Spec](use_cases/UC-031-import-browser-bookmarks.md), [UC-031 Diagram](use_cases.puml)
**Priority:** Low
**Complexity:** Medium-Low

---

## Supported Format

**Netscape Bookmark HTML** — the universal interchange format supported by all major browsers.

### How users export from each browser

| Browser | Steps |
|---|---|
| **Brave** | Menu → Bookmarks → Bookmark manager → ⋮ → Export bookmarks |
| **Chrome** | Menu → Bookmarks and lists → Bookmark manager → ⋮ → Export bookmarks |
| **Firefox** | Menu → Bookmarks → Manage bookmarks → Import and Backup → Export Bookmarks to HTML |
| **Safari** | File → Export Bookmarks… |
| **Edge** | Menu → Favorites → ⋯ → Export favorites |

### Netscape HTML format structure

All browsers produce a file conforming to the same HTML structure:

```html
<!DOCTYPE NETSCAPE-Bookmark-file-1>
<!--This is an automatically generated file.
    It will be read and overwritten.
    Do Not Edit! -->
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3 ADD_DATE="1234567890" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks Bar</H3>
    <DL><p>
        <DT><A HREF="https://example.com" ADD_DATE="1234567890"
        LAST_VISIT="1234567890" LAST_MODIFIED="1234567890">Example Title</A>
        <DT><H3 ADD_DATE="1234567890">Subfolder</H3>
        <DL><p>
            <DT><A HREF="https://example.org" ADD_DATE="1234567890">Another Title</A>
        </DL><p>
    </DL><p>
</DL><p>
```

**Key elements:**

| Element | Meaning |
|---|---|
| `<DL>` | Folder container (definition list). Nesting = hierarchy. |
| `<DT><H3>` | Folder heading. `ADD_DATE` = Unix epoch seconds. |
| `<DT><A HREF="...">` | Bookmark. `HREF` = URL, text content = title. |
| `ADD_DATE`, `LAST_VISIT`, `LAST_MODIFIED` | Optional Unix epoch timestamps (seconds). |

---

## Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| **Format** | Netscape HTML only | Universal export format; no need to support internal browser formats |
| **Duplicates** | Import anyway | Allow duplicates; user can de-duplicate later |
| **Transaction** | Single transaction | All-or-nothing; simpler error handling |
| **Target location** | Collection root | Imported folder hierarchy placed at collection root level |
| **Auto-tagging** | `imported=YYYY-MM-DD_N` | All imported bookmarks receive an auto-generated tag for traceability |

---

## Architecture

### New Maven dependencies

```xml
<!-- HTML parsing -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.3</version>
</dependency>

<!-- Multipart form upload support -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-multipart</artifactId>
</dependency>
```

### New classes

| Class | Layer | Responsibility |
|---|---|---|
| `NetscapeBookmarkParser` | Infrastructure | Parse Netscape HTML into a tree of `ParsedFolder` / `ParsedBookmark` DTOs using Jsoup |
| `ParsedFolder` | DTO | In-memory representation of a parsed folder (name, children) |
| `ParsedBookmark` | DTO | In-memory representation of a parsed bookmark (title, url) |
| `BookmarkImportService` | Service | Orchestrate parsing + batch creation of Folder, Bookmark, Tag entities |
| `ImportResource` | Resource | `POST /collections/{id}/import` endpoint (multipart file upload) |

### Endpoint

```
POST /api/collections/{collectionId}/import
Content-Type: multipart/form-data

Parameters:
  file: the exported bookmarks HTML file

Response: 200 OK with import summary
{
  "foldersCreated": 12,
  "bookmarksCreated": 87,
  "importTag": "imported=2026-04-03_1"
}

Errors:
  400 — invalid file (not HTML, empty, malformed)
  403 — no access to collection
  413 — file too large
```

### Flow

```
User uploads HTML file
       │
       ▼
ImportResource
  ├── authorizationService.requireCollectionAccess(collectionId)
  ├── validate file (non-null, .html extension, size limit)
  └── bookmarkImportService.importBookmarks(collectionId, inputStream)
       │
       ▼
BookmarkImportService
  ├── netscapeBookmarkParser.parse(inputStream)
  │       │
  │       ▼
  │   Produces tree of ParsedFolder / ParsedBookmark
  │
  ├── Create or find import tag: "imported=YYYY-MM-DD_N"
  │       (N = incrementing counter to make tag unique per import run)
  │
  ├── Recursively create Folder entities (top-down, resolving parent IDs)
  │
  └── Create Bookmark entities (each tagged with import tag)
       │
       ▼
Return ImportSummaryJson
```

### Authorization

Standard pattern: `ImportResource` calls `authorizationService.requireCollectionAccess(collectionId)` before delegating to the service. The service does not perform authorization checks.

### Auto-tagging

Each import run creates a new `Tag` entity in the target collection:
- Name: `imported=YYYY-MM-DD_N` (e.g. `imported=2026-04-03_1`)
- Color: auto-assigned or default
- Every imported bookmark (across all folders) receives this tag

The tag name is idempotent-friendly: if the user imports twice on the same day, they get `imported=2026-04-03_1` and `imported=2026-04-03_2`, making each batch distinguishable.

---

## Implementation Steps

1. Add `jsoup` and `quarkus-resteasy-multipart` dependencies to `pom.xml`
2. Create `ParsedFolder` and `ParsedBookmark` DTO records
3. Create `NetscapeBookmarkParser` — recursive Jsoup walk of `<DL>/<DT>/<H3>/<A>` elements
4. Create `BookmarkImportService` with `importBookmarks(ID<Collection>, InputStream)` method
5. Create `ImportResource` with multipart upload endpoint
6. Write integration test with sample Netscape HTML fixture file
7. Run `./mvnw compile` and `npm run type-check` to verify

---

## Files to Create/Modify

| Action | File |
|---|---|
| **Modify** | `api/pom.xml` — add jsoup + multipart deps |
| **Create** | `api/src/main/java/org/linkweave/application/import_/ParsedFolder.java` |
| **Create** | `api/src/main/java/org/linkweave/application/import_/ParsedBookmark.java` |
| **Create** | `api/src/main/java/org/linkweave/application/import_/NetscapeBookmarkParser.java` |
| **Create** | `api/src/main/java/org/linkweave/application/import_/ImportSummaryJson.java` |
| **Create** | `api/src/main/java/org/linkweave/application/import_/BookmarkImportService.java` |
| **Create** | `api/src/main/java/org/linkweave/infrastructure/rest/ImportResource.java` |
| **Create** | `api/src/test/java/org/linkweave/application/import_/NetscapeBookmarkParserTest.java` |
| **Create** | `api/src/test/java/org/linkweave/application/import_/BookmarkImportServiceTest.java` |
| **Create** | `api/src/test/resources/__files/bookmarks-sample.html` |

---

## Open Questions

- **File size limit:** What's the max upload size? Suggest 5 MB (covers even very large bookmark collections).
- **Import tag color:** Should the auto-generated tag have a specific color to stand out?
- **Extension whitelist:** The existing `esc.upload.extension.whitelist` config should be updated to include `.html`.
