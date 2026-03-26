# Requirements Catalog

**Project:** Chainlink Bookmark Manager
**Source:** [vision.md](vision.md)
**Date:** 2026-03-26

---

## User Roles

| Role             | Description                                                                |
|------------------|----------------------------------------------------------------------------|
| User             | An individual who owns a collection and manages bookmarks, folders, and tags |
| Collection Owner | A User who created a collection and can share it with others               |

---

## Functional Requirements

| ID     | Title                       | User Story                                                                                                                           | Priority | Status |
|--------|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------|----------|--------|
| FR-001 | Auto-Provision on First Login | As a User, I want the system to automatically create a default collection for me the first time I identify myself, so that I can start saving bookmarks without any manual setup. | High     | Open   |
| FR-002 | Auto-Navigate to Collection    | As a User, I want the system to take me directly to my collection when I have only one collection or have set a default collection, so that I do not have to choose every time I open the application. | High     | Open   |
| FR-003 | List My Collections            | As a User, I want to see a list of all collections I have access to so that I can navigate to the right bookmark collection when I have more than one collection and no default is set. | High     | Open   |
| FR-004 | Set Default Collection         | As a User, I want to designate one collection as my default so that I am taken directly to it on every login without needing to select it. | Medium   | Open   |
| FR-005 | Create Bookmark             | As a User, I want to create a bookmark with a URL and title so that I can save web resources.                                        | High     | Open   |
| FR-006 | View Bookmarks              | As a User, I want to view all bookmarks in my collection so that I can browse my saved resources.                                    | High     | Open   |
| FR-007 | Edit Bookmark               | As a User, I want to edit a bookmark's URL, title, and description so that I can keep my bookmarks up to date.                       | High     | Open   |
| FR-008 | Delete Bookmark             | As a User, I want to delete a bookmark so that I can remove resources I no longer need.                                              | High     | Open   |
| FR-009 | Create Folder               | As a User, I want to create a folder so that I can organize bookmarks into groups.                                                   | High     | Open   |
| FR-010 | View Folders                | As a User, I want to view all folders in my collection so that I can see my organizational structure.                                | High     | Open   |
| FR-011 | Rename Folder               | As a User, I want to rename a folder so that I can keep my organizational structure accurate.                                        | Medium   | Open   |
| FR-012 | Nest Folders                | As a User, I want to nest folders inside other folders so that I can create a hierarchy for my bookmarks.                            | Medium   | Open   |
| FR-013 | Move Bookmark to Folder     | As a User, I want to move a bookmark into a folder so that I can organize my bookmarks.                                              | High     | Open   |
| FR-014 | Delete Folder               | As a User, I want to delete a folder so that I can remove organizational structures I no longer need.                                | Medium   | Open   |
| FR-015 | Create Tag                  | As a User, I want to create a tag so that I can label bookmarks with custom categories.                                              | High     | Open   |
| FR-016 | View Tags                   | As a User, I want to view all tags in my collection so that I can see and manage my available labels.                                | High     | Open   |
| FR-017 | Rename Tag                  | As a User, I want to rename a tag so that I can correct or improve my labeling scheme.                                               | Medium   | Open   |
| FR-018 | Delete Tag                  | As a User, I want to delete a tag so that I can remove labels I no longer use.                                                       | Medium   | Open   |
| FR-019 | Apply Tag to Bookmark       | As a User, I want to apply one or more tags to a bookmark so that I can categorize and filter my bookmarks.                          | High     | Open   |
| FR-020 | Remove Tag from Bookmark    | As a User, I want to remove a tag from a bookmark so that I can correct miscategorized bookmarks.                                    | Medium   | Open   |
| FR-021 | Filter Bookmarks by Tag     | As a User, I want to filter bookmarks by tag so that I can quickly find bookmarks in a category.                                     | High     | Open   |
| FR-022 | Filter Bookmarks by Folder  | As a User, I want to filter bookmarks by folder so that I can browse bookmarks within a specific folder.                             | High     | Open   |
| FR-023 | Share Collection               | As a Collection Owner, I want to share my collection with another user so that we can collaborate on a shared bookmark collection.   | Medium   | Open   |
| FR-024 | Access Shared Collection       | As a User, I want to access a collection shared with me so that I can view and manage its bookmarks.                                 | Medium   | Open   |

---

## Non-Functional Requirements

| ID      | Title                   | Requirement                                                                                          | Category     | Priority | Status |
|---------|-------------------------|------------------------------------------------------------------------------------------------------|--------------|----------|--------|
| NFR-001 | Page Load Time          | All page loads must complete within 2 seconds under normal load (up to 10 concurrent users).         | Performance  | High     | Open   |
| NFR-002 | API Response Time       | All API responses must be returned within 500 ms under normal load (up to 10 concurrent users).      | Performance  | High     | Open   |
| NFR-003 | Access Control          | A user must not be able to read or modify resources belonging to a collection they have no access to; violations must return HTTP 403. | Security     | High     | Open   |
| NFR-004 | Audit Coverage          | All create, update, and delete operations on Bookmark, Folder, and Tag entities must be automatically tracked using Hibernate Envers; no custom audit table is required. | Auditability | Low      | Open   |
| NFR-005 | Code Coverage           | Service and repository classes must maintain a minimum of 80% line coverage as measured by the CI test run. | Testability  | Medium   | Open   |
| NFR-006 | Use Case Test Coverage  | Every functional requirement must be traceable to at least one automated integration test that verifies the main success scenario. | Testability  | Medium   | Open   |

---

## Constraints

| ID    | Title              | Constraint                                                                 | Category  | Priority | Status |
|-------|--------------------|----------------------------------------------------------------------------|-----------|----------|--------|
| C-001 | Backend Framework  | Backend must be implemented using Quarkus.                                 | Technical | High     | Open   |
| C-002 | Frontend Framework | Frontend must be implemented using Vue.js with Pinia for state management. | Technical | High     | Open   |
| C-003 | Single Database    | All collections, bookmarks, folders, tags, and access control must be stored in a single SQLite database file. Multi-tenancy is achieved via `collection_id` foreign keys. (Note: Multi-database architecture may be considered for future scaling.) | Technical | High     | Open   |
| C-004 | Deployment Model   | The application must be deployable as a self-hosted installation without external cloud dependencies. | Technical | High     | Open   |
| C-005 | User Identity      | Users must be identified by a stable, unique username. For this release, Quarkus form-based authentication with an in-memory user store is used. OIDC integration is planned for a future release. | Technical | High     | Open   |
| C-006 | Primary Keys       | All entity primary keys must be UUIDs generated server-side; auto-increment sequences must not be used. | Technical | High     | Open   |
| C-007 | Out of Scope       | User management (creating, editing, deleting user accounts by an admin) is out of scope for this release. | Business  | High     | Open   |
