# Use Case: Screenshot Cache Cleanup Job

## Overview

**Use Case ID:** UC-055
**Use Case Name:** Screenshot Cache Cleanup Job
**Primary Actor:** Scheduler
**Secondary Actors:** Operator (configures job parameters)
**Goal:** Keep the on-disk screenshot cache directory within a configurable size budget so that long-running deployments do not accumulate unbounded disk usage.
**Status:** Open

## Traceability

**Maps to:** FR-066, C-012
**Design document:** [plans/screenshot-previews.md](../plans/screenshot-previews.md)

---

## Preconditions

- The screenshot cache directory exists on the filesystem.
- The scheduler is running (Quarkus scheduler).

## Main Success Scenario

1. The scheduler triggers the cleanup job on the configured cron schedule (default: Sunday 03:00).
2. The job computes the total size of all files in the screenshot cache directory.
3. If the total size is within the configured budget (default 200 MB), the job logs a "within budget" message and ends.
4. If the total size exceeds the budget, the job iterates bookmarks across all collections in ascending creation-timestamp order, skipping any bookmark younger than the configured minimum age (default 28 days).
5. For each eligible bookmark, the job computes the cache key and deletes the associated `.bin` and `.meta` files.
6. After each deletion, the job checks whether the remaining size is now within budget. If so, it stops iterating.
7. The job logs a summary: number of entries evicted, bytes freed, final directory size, age of the oldest evicted entry.

## Alternative Flows

### A1: Cache Directory Does Not Exist

**Trigger:** The configured cache directory path does not exist on the filesystem.
**Flow:**

1. The job logs a "directory does not exist; cleanup skipped" message.
2. Use case ends.

### A2: Job Disabled via Configuration

**Trigger:** The `linkweave.screenshot.cache-cleanup.enabled` property is set to `false`.
**Flow:**

1. The Quarkus skip predicate prevents execution.
2. Use case ends.

### A3: Budget Exceeded After Exhausting Eligible Bookmarks

**Trigger:** All eligible bookmarks have been processed but the cache is still over budget.
**Flow:**

1. The job logs a warning with the remaining size and the number of entries evicted.
2. Use case ends. The cache will be further reduced on the next scheduled run as more bookmarks become eligible.

### A4: Individual File Deletion Fails

**Trigger:** An I/O error occurs while deleting a cache file.
**Flow:**

1. The error is logged. The file is treated as zero bytes freed.
2. The job continues processing the next bookmark.
3. Use case continues.

## Postconditions

### Success Postconditions

- The screenshot cache directory size is at or below the configured budget.
- Individual file deletion failures did not abort the overall cleanup.
- No database rows were modified (cache cleanup touches files only).

### Failure Postconditions

- The cache directory may still exceed the budget if too few bookmarks were eligible or too many file deletions failed.
- The next scheduled run will attempt cleanup again.

## Business Rules

### BR-123: Size Budget

The configurable maximum cache size (`linkweave.screenshot.cache-cleanup.max-size`, default `200MB`) is parsed from human-readable units (KB, MB, GB). Sizes are expressed in binary units (1 MB = 1024 × 1024 bytes).

### BR-124: Minimum Bookmark Age

Bookmarks younger than the configured minimum age (`linkweave.screenshot.cache-cleanup.min-bookmark-age`, default `28D`) are never evicted. This protects recently added bookmarks whose screenshots are likely still being viewed.

### BR-125: File-Only Operation

The cleanup job deletes cache files only. It never modifies or deletes database rows. The cache is purely a performance optimization and can be fully rebuilt on demand.

### BR-126: Origin-Level Deduplication

When iterating bookmarks, the job tracks which canonical origins have already been processed. Bookmarks sharing the same origin (and therefore the same cache entry) are only processed once per cleanup run.
