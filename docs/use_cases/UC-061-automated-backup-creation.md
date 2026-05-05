# Use Case: Automated Backup Creation

## Overview

**Use Case ID:** UC-061
**Use Case Name:** Automated Backup Creation
**Primary Actor:** User
**Secondary Actors:** Scheduler
**Goal:** Automatically create periodic backups of a user's bookmarks, folders, tags, and collection data so that data can be restored in case of accidental deletion, corruption, or migration.
**Status:** Open

---

## Preconditions

- The user is authenticated.
- The user has at least one collection with data.

## Main Success Scenario

1. User enables automated backups in settings.
2. User configures backup frequency (e.g. daily, weekly) and retention policy (e.g. keep last 5 backups).
3. Scheduler triggers a backup job at the configured interval.
4. System exports the user's data (collections, bookmarks, folders, tags, and sharing metadata) into a portable format (e.g. JSON or SQLite snapshot).
5. System stores the backup in the configured location (local filesystem, download, or external storage).
6. System verifies the backup integrity.
7. System applies the retention policy and removes expired backups.

## Alternative Flows

### A1: Backup on Demand

**Trigger:** User clicks "Create Backup Now" in settings.
**Flow:

1. System performs steps 4–6 immediately.
2. Backup is stored outside the retention schedule.
3. Use case ends.

### A2: Backup Storage Full

**Trigger:** Configured backup location has insufficient space.
**Flow:

1. System applies the retention policy aggressively to free space.
2. If still insufficient, system notifies the user with an error.
3. Backup is not created.

### A3: Restore from Backup

**Trigger:** User selects a backup to restore.
**Flow:

1. System displays a preview of the backup contents (collection count, bookmark count, date).
2. User confirms restoration.
3. System replaces current data with backup data.
4. User is logged out and must re-authenticate.

### A4: Export Without Scheduling

**Trigger:** User wants a one-time export without enabling scheduled backups.
**Flow:

1. User clicks "Export Data" in settings.
2. System generates the export and downloads it as a file.
3. No retention policy or scheduling is involved.

## Postconditions

### Success Postconditions

- A backup file exists containing the user's complete data.
- The backup is verifiable and restorable.
- Expired backups have been cleaned up per the retention policy.

### Failure Postconditions

- No backup is created; the user is notified of the failure.
- Existing data remains intact and unaffected.

## Business Rules

### BR-061-1: Backup Scope

A backup MUST include all collections the user owns or has access to, including bookmarks, folder hierarchies, tags, tag assignments, and sharing relationships. It MUST NOT include other users' private data.

### BR-061-2: Portable Format

The backup format MUST be self-contained and restorable without the running application (e.g. a JSON archive or SQLite file dump). This enables migration between instances.

### BR-061-3: Encryption at Rest

If backups are stored on the server, they MUST be encrypted. The backup must not expose user data in plaintext on the filesystem.
