import type { BookmarkJson, FolderJson } from '@/api/generated'
import { normalizeUrl } from '@/lib/url'
import { computed, type Ref } from 'vue'

export interface DuplicateBookmark {
  id: string
  title: string
  folderId: string | null
  folderName: string | null
  /**
   * The form is about to re-create *this* entry: same normalized url, same
   * title, same target folder. Distinguishes the accidental double save from a
   * legitimate second entry for the same page (e.g. "… - LOCAL" filed next to
   * the plain one, or the same link kept in two folders).
   *
   * Only ever changes what the warning says — BR-026 keeps duplicate urls legal,
   * so saving is never blocked.
   */
  isExact: boolean
}

export interface UseDuplicateCheckOptions {
  folders?: Ref<FolderJson[]>
  excludeBookmarkId?: Ref<string | undefined>
  /**
   * The form's current title and target folder. Both are needed to flag an exact
   * duplicate; omit them and every match is reported as inexact, which is the
   * url-only behaviour callers had before.
   */
  title?: Ref<string | undefined>
  folderId?: Ref<string | null | undefined>
}

/** Treats "unfiled" as one value however the caller spells it. */
function normalizeFolderId(folderId: string | null | undefined): string | null {
  return folderId === undefined || folderId === null || folderId === '' ? null : folderId
}

export function useDuplicateCheck(
  url: Ref<string | undefined>,
  bookmarks: Ref<BookmarkJson[]>,
  options?: UseDuplicateCheckOptions,
) {
  const duplicates = computed<DuplicateBookmark[]>(() => {
    const rawUrl = url.value
    if (!rawUrl) return []

    const normalized = normalizeUrl(rawUrl)
    const excludeId = options?.excludeBookmarkId?.value
    const folders = options?.folders?.value ?? []
    const folderMap = new Map(folders.map((f) => [f.id, f.data.name]))

    // Undefined when the caller does not track them — then nothing is ever exact.
    const formTitle = options?.title?.value?.trim()
    const formFolderId = options?.folderId ? normalizeFolderId(options.folderId.value) : undefined

    const result: DuplicateBookmark[] = []

    for (const b of bookmarks.value) {
      if (!b.data.url) continue
      if (excludeId && b.id === excludeId) continue
      if (normalizeUrl(b.data.url) !== normalized) continue

      const folderId = normalizeFolderId(b.data.folderId)
      result.push({
        id: b.id,
        title: b.data.title,
        folderId,
        folderName: folderId ? (folderMap.get(folderId) ?? null) : null,
        isExact:
          formTitle !== undefined &&
          formFolderId !== undefined &&
          b.data.title.trim() === formTitle &&
          folderId === formFolderId,
      })
    }

    return result
  })

  /**
   * The entry the form would duplicate outright, if any. Lets the UI answer the
   * question behind the accidental double save — "did my last save work?" — by
   * pointing at the bookmark that is already there.
   */
  const exactDuplicate = computed<DuplicateBookmark | null>(
    () => duplicates.value.find((d) => d.isExact) ?? null,
  )

  return { duplicates, exactDuplicate }
}
