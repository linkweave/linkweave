# Use Case: Favicon Cache Cleanup Job

## Overview

**Use Case ID:** UC-051
**Use Case Name:** Favicon Cache Cleanup Job
**Primary Actor:** System (Scheduler)
**Goal:** Keep the on-disk favicon cache (UC-050) within an operator-defined size budget by periodically evicting cache entries belonging to the oldest bookmarks across all collections, so that long-running deployments do not accumulate unbounded disk usage.
**Status:** Implemented

## Traceability

**Maps to:** FR-064

---

## Preconditions

- The favicon cache directory configured for UC-050 exists and is writable by the application.
- The application has been running long enough for at least one favicon to have been cached.

## Trigger

- A scheduler fires the cleanup job once per week (configurable via `linkweave.favicon.cache-cleanup.cron`, default `0 0 3 ? * SUN` — Sunday 03:00 server time).

## Main Success Scenario

1. Scheduler invokes the favicon cache cleanup job.
2. Job computes the **current cache size** by summing the byte size of every regular file in the cache directory (both `*.bin` payload files and `*.meta` sidecars).
3. Job compares the size to the configured threshold `linkweave.favicon.cache-cleanup.max-size` (default `40MB`).
4. **If size is at or below the threshold:** job logs the current size and exits. Use case ends.
5. **If size exceeds the threshold:**
    1. Job loads bookmarks across **all collections** ordered by `lastClickedAt` ascending (nulls last), then by `timestampErstellt` ascending, excluding soft-deleted bookmarks. The effective eviction timestamp for each bookmark is `lastClickedAt` if present, falling back to `timestampErstellt`.
   2. For each bookmark, the job checks the bookmark's age: if it is younger than the configured minimum age `linkweave.favicon.cache-cleanup.min-bookmark-age` (default `28d` — four weeks), the bookmark and all subsequent (younger) bookmarks are skipped and the iteration ends.
   3. The job derives the **canonical origin** of the bookmark URL (same logic as UC-050) and computes the SHA-256 cache key.
   4. If `<key>.bin` and/or `<key>.meta` exist in the cache directory, they are deleted and the running cache-size estimate is decremented by their on-disk size.
   5. After each deletion, the job re-checks against the threshold. As soon as the running size estimate is at or below the threshold, the job stops iterating.
6. Job logs how many cache entries were evicted, total bytes freed, final cache size, and the highest bookmark age it had to evict to (so operators can decide whether to raise the budget).

## Alternative Flows

### A1: Iteration Exhausted Without Reaching Threshold

**Trigger:** All bookmarks at least 4 weeks old have been processed and their cache entries removed, but the cache is still above the threshold.
**Flow:**

1. Job logs a warning containing: configured threshold, residual cache size, count of cache files that were *not* mapped to any bookmark older than 4 weeks (these are typically files for newly-cached bookmarks that the policy refuses to evict).
2. Job exits without raising an error so the schedule continues to fire.
3. Use case ends.

### A2: Cache Directory Missing

**Trigger:** The cache directory does not exist when the job runs.
**Flow:**

1. Job logs an info message and exits cleanly.
2. Use case ends.

### A3: I/O Failure During Eviction

**Trigger:** Deleting an individual cache file fails (permissions, locked file, etc.).
**Flow:**

1. Job logs the failure for that specific file at WARN level and continues with the next bookmark.
2. The size estimate is *not* decremented for that file.
3. Use case continues at step 5.5 of the main flow.

### A4: Concurrent Fetch During Cleanup

**Trigger:** The favicon proxy (UC-050) writes a new cache entry while cleanup is running.
**Flow:**

1. Cleanup operates on a snapshot listing of cache files captured at the start of step 2; new files written mid-job are not seen this run.
2. Atomic-rename writes (UC-050) ensure that a partially written file is never observed as a deletable entry.
3. Use case continues unaffected; any newly written file will be considered next run.

## Postconditions

### Success Postconditions

- The cache directory's total size is at or below `linkweave.favicon.cache-cleanup.max-size`, or — if A1 fired — every evictable bookmark's cache entry has been removed.
- No bookmark rows, collection rows, or any other user-visible data have been modified.
- A log entry summarising the run is written.

### Failure Postconditions

- If the job throws an unhandled exception, the scheduler retries on its next fire; no partial cleanup state needs to be reconciled (deletions are idempotent).

## Business Rules

### BR-108: Cache-Only Eviction Scope

The cleanup job may only delete files inside the configured favicon cache directory. It must never delete, soft-delete, or mutate any database entity, including bookmarks, collections, folders, tags, or audit records.

### BR-109: Minimum Bookmark Age Before Eviction

A bookmark whose `timestampErstellt` is younger than `linkweave.favicon.cache-cleanup.min-bookmark-age` (default 4 weeks) must not have its origin's cache entry evicted by this job. This protects recently created bookmarks from a cold-cache experience the moment a user adds them.

### BR-110: Eviction Order

When the threshold is exceeded, eligible bookmarks are processed in ascending order of `lastClickedAt` (nulls last), then `timestampErstellt`. The effective eviction timestamp for ordering is `lastClickedAt` if present, falling back to `timestampErstellt`. Iteration stops as soon as the running cache-size estimate falls back to the threshold.

### BR-111: Origin-Level Granularity

Cache entries are keyed by canonical origin, not by bookmark. Evicting the entry for `https://example.com` removes that single shared cache entry even if multiple bookmarks point at the same origin. The job does **not** attempt to refcount bookmarks per origin: any bookmark older than the minimum age is sufficient justification to evict its origin's entry, since a cache miss simply triggers a re-fetch on next view.

### BR-112: Schedule Configurability

The cron expression, size threshold, and minimum bookmark age are application properties (`linkweave.favicon.cache-cleanup.cron`, `.max-size`, `.min-bookmark-age`) and must be settable per environment without code changes. Setting `linkweave.favicon.cache-cleanup.enabled=false` disables the schedule entirely.

### BR-113: Cleanup Is Best-Effort

The job must not throw out of its scheduled execution on partial I/O failures. Errors on individual files are logged and skipped (A3) so a single locked or unreadable file does not block eviction of the rest.
