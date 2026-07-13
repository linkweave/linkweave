# Use Case: Invite User to Collection as Viewer

## Overview

**Use Case ID:** UC-100   
**Use Case Name:** Invite User to Collection as Viewer   
**Primary Actor:** Collection Owner   
**Goal:** Grant another user read-only access to a collection so they can browse and search its bookmarks without being able to change anything.   
**Status:** Draft   

## Traceability

**Notes:** Competitive parity with Karakeep, whose lists support collaborator
invites with a viewer role (see `docs/marketing/market-analysis.md`). Extends
UC-023 (Share Collection) and UC-024 (Access Shared Collection) with a new
read-only role below `MEMBER` in the existing role hierarchy.

---

## Preconditions

- The actor is authenticated.
- The actor is Owner or Admin of the collection they want to share.
- The target user exists in the system.

## Main Success Scenario

1. Collection Owner opens the collection settings.
2. Collection Owner selects the option to share the collection.
3. System displays the share form with a role selection offering "Member" and "Viewer".
4. Collection Owner enters the username of the user to share with and selects the role "Viewer".
5. System validates that the entered username exists.
6. System grants the target user viewer access to the collection.
7. System confirms the share with a success message and lists the user in the member list with a "Viewer" role badge.

## Alternative Flows

### A1: Username Does Not Exist

**Trigger:** The entered username is not found in the system (step 5).
**Flow:**

1. System displays an error message: "User not found."
2. Collection Owner corrects the username or cancels.
3. Use case continues at step 4.

### A2: User Already Has Access

**Trigger:** The target user already holds a role on this collection (step 5).
**Flow:**

1. System displays an informational message showing the user's current role.
2. Collection Owner may change the user's role instead (e.g. demote a Member to Viewer or promote a Viewer to Member) or cancel.
3. If the Collection Owner confirms a role change, the system updates the existing access record; no duplicate record is created.
4. Use case ends.

### A3: Owner Attempts to Share With Themselves

**Trigger:** The entered username matches the actor's own username (step 5).
**Flow:**

1. System displays an error message: "You cannot share a collection with yourself."
2. Use case continues at step 4.

### A4: Viewer Attempts a Modification

**Trigger:** A user holding the Viewer role attempts to create, edit, move, or delete content in the collection (after the share is completed).
**Flow:**

1. System does not offer editing actions (add bookmark, edit, delete, drag-and-drop, tag management) in the viewer's interface.
2. If a modification request reaches the server anyway, the system rejects it as forbidden and the collection remains unchanged.

## Postconditions

### Success Postconditions

- The target user holds the Viewer role on the collection.
- The target user can browse, search, and filter bookmarks in the collection (via UC-024) but cannot modify anything.
- The user appears in the collection's member list with the Viewer role.

### Failure Postconditions

- No new access is granted and no existing role is changed.
- The collection's access list remains unchanged.

## Business Rules

### BR-100-1: Viewer Role Is Read-Only

A Viewer can read bookmarks, folders, tags, and properties of the collection,
including via search and filters. A Viewer cannot create, edit, move, or
delete bookmarks, folders, tags, or properties, cannot manage members, and
cannot change collection settings.

### BR-100-2: Role Hierarchy

The Viewer role is the lowest privilege level in the collection role
hierarchy: `VIEWER < MEMBER < ADMIN < OWNER`. Every privilege check that
requires at least Member privilege is denied for Viewers.

### BR-100-3: Who May Invite Viewers

Owners and Admins may invite users as Viewers and change roles between Viewer
and Member. Only the Owner may promote to or demote from Admin (unchanged
from the existing role model).

### BR-100-4: No Duplicate Access

A user holds at most one access record per collection (BR-008). Inviting a
user who already has access results in a role change on the existing record,
never a second record.
