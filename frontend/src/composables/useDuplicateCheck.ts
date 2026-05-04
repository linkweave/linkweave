import type { BookmarkJson, FolderJson } from '@/api/generated'
import { normalizeUrl } from '@/lib/url'
import { computed, type Ref } from 'vue'

export interface DuplicateBookmark {
  id: string
  title: string
  folderName: string | null
}

export interface UseDuplicateCheckOptions {
  folders?: Ref<FolderJson[]>
  excludeBookmarkId?: Ref<string | undefined>
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
    const result: DuplicateBookmark[] = []

    for (const b of bookmarks.value) {
      if (!b.data.url) continue
      if (excludeId && b.id === excludeId) continue
      if (normalizeUrl(b.data.url) === normalized) {
        result.push({
          id: b.id,
          title: b.data.title,
          folderName: b.data.folderId ? (folderMap.get(b.data.folderId) ?? null) : null,
        })
      }
    }

    return result
  })

  return { duplicates }
}
