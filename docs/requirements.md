# Requirements Catalog

**Project:** Chainlink Bookmark Manager
**Source:** [vision.md](vision.md)
**Date:** 2026-03-26

---

## User Roles

| Role           | Description                                                              |
|----------------|--------------------------------------------------------------------------|
| User           | An individual who owns a project and manages bookmarks, folders, and tags |
| Project Owner  | A User who created a project and can share it with others                |

---

## Functional Requirements

| ID     | Title                       | User Story                                                                                                                           | Priority | Status |
|--------|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------|----------|--------|
| FR-001 | Auto-Provision on First Login | As a User, I want the system to automatically create a default project for me the first time I identify myself, so that I can start saving bookmarks without any manual setup. | High     | Open   |
| FR-002 | Auto-Navigate to Project    | As a User, I want the system to take me directly to my project when I have only one project or have set a default project, so that I do not have to choose every time I open the application. | High     | Open   |
| FR-003 | List My Projects            | As a User, I want to see a list of all projects I have access to so that I can navigate to the right bookmark collection when I have more than one project and no default is set. | High     | Open   |
| FR-004 | Set Default Project         | As a User, I want to designate one project as my default so that I am taken directly to it on every login without needing to select it. | Medium   | Open   |
| FR-005 | Create Bookmark             | As a User, I want to create a bookmark with a URL and title so that I can save web resources.                                        | High     | Open   |
| FR-006 | View Bookmarks              | As a User, I want to view all bookmarks in my project so that I can browse my saved resources.                                       | High     | Open   |
| FR-007 | Edit Bookmark               | As a User, I want to edit a bookmark's URL, title, and description so that I can keep my bookmarks up to date.                       | High     | Open   |
| FR-008 | Delete Bookmark             | As a User, I want to delete a bookmark so that I can remove resources I no longer need.                                              | High     | Open   |
| FR-009 | Create Folder               | As a User, I want to create a folder so that I can organize bookmarks into groups.                                                   | High     | Open   |
| FR-010 | Nest Folders                | As a User, I want to nest folders inside other folders so that I can create a hierarchy for my bookmarks.                            | Medium   | Open   |
| FR-011 | Move Bookmark to Folder     | As a User, I want to move a bookmark into a folder so that I can organize my bookmarks.                                              | High     | Open   |
| FR-012 | Delete Folder               | As a User, I want to delete a folder so that I can remove organizational structures I no longer need.                                | Medium   | Open   |
| FR-013 | Create Tag                  | As a User, I want to create a tag so that I can label bookmarks with custom categories.                                              | High     | Open   |
| FR-014 | Apply Tag to Bookmark       | As a User, I want to apply one or more tags to a bookmark so that I can categorize and filter my bookmarks.                          | High     | Open   |
| FR-015 | Remove Tag from Bookmark    | As a User, I want to remove a tag from a bookmark so that I can correct miscategorized bookmarks.                                    | Medium   | Open   |
| FR-016 | Filter Bookmarks by Tag     | As a User, I want to filter bookmarks by tag so that I can quickly find bookmarks in a category.                                     | High     | Open   |
| FR-017 | Filter Bookmarks by Folder  | As a User, I want to filter bookmarks by folder so that I can browse bookmarks within a specific folder.                             | High     | Open   |
| FR-018 | Share Project               | As a Project Owner, I want to share my project with another user so that we can collaborate on a shared bookmark collection.         | Medium   | Open   |
| FR-019 | Access Shared Project       | As a User, I want to access a project shared with me so that I can view and manage its bookmarks.                                    | Medium   | Open   |
| FR-020 | View Audit Log              | As a User, I want to view a log of changes made in my project so that I can track who changed what and when.                         | Low      | Open   |

---

## Non-Functional Requirements

| ID      | Title                   | Requirement                                                                                          | Category     | Priority | Status |
|---------|-------------------------|------------------------------------------------------------------------------------------------------|--------------|----------|--------|
| NFR-001 | Page Load Time          | All page loads must complete within 2 seconds under normal load (up to 10 concurrent users).         | Performance  | High     | Open   |
| NFR-002 | API Response Time       | All API responses must be returned within 500 ms under normal load (up to 10 concurrent users).      | Performance  | High     | Open   |
| NFR-003 | Access Control          | A user must not be able to read or modify resources belonging to a project they have no access to; violations must return HTTP 403. | Security     | High     | Open   |
| NFR-004 | Audit Coverage          | All create, update, and delete operations on bookmarks, folders, and tags must produce an audit record within the same transaction. | Auditability | Low      | Open   |
| NFR-005 | Code Coverage           | Service and repository classes must maintain a minimum of 80% line coverage as measured by the CI test run. | Testability  | Medium   | Open   |
| NFR-006 | Use Case Test Coverage  | Every functional requirement must be traceable to at least one automated integration test that verifies the main success scenario. | Testability  | Medium   | Open   |

---

## Constraints

| ID    | Title              | Constraint                                                                 | Category  | Priority | Status |
|-------|--------------------|----------------------------------------------------------------------------|-----------|----------|--------|
| C-001 | Backend Framework  | Backend must be implemented using Quarkus.                                 | Technical | High     | Open   |
| C-002 | Frontend Framework | Frontend must be implemented using Vue.js with Pinia for state management. | Technical | High     | Open   |
| C-003 | Single Database    | All projects, bookmarks, folders, tags, and access control must be stored in a single SQLite database file. Multi-tenancy is achieved via `project_id` foreign keys. (Note: Multi-database architecture may be considered for future scaling.) | Technical | High     | Open   |
| C-004 | Deployment Model   | The application must be deployable as a self-hosted installation without external cloud dependencies. | Technical | High     | Open   |
| C-005 | User Identity      | Users must be identified by a stable, unique username. For this release, Quarkus form-based authentication with an in-memory user store is used. OIDC integration is planned for a future release. | Technical | High     | Open   |
| C-006 | Out of Scope       | User management (creating, editing, deleting user accounts by an admin) is out of scope for this release. | Business  | High     | Open   |
