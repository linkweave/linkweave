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
