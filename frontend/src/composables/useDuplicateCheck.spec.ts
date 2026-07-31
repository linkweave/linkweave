import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import type { BookmarkJson, FolderJson } from '@/api/generated'
import { useDuplicateCheck } from './useDuplicateCheck'

function bookmark(
  id: string,
  url: string,
  title: string,
  folderId: string | null = null,
): BookmarkJson {
  return { id, data: { url, title, folderId } } as unknown as BookmarkJson
}

function folder(id: string, name: string): FolderJson {
  return { id, data: { name } } as unknown as FolderJson
}

const INBOX = folder('f-inbox', 'Inbox')
const ARCHIVE = folder('f-archive', 'Archive')

describe('useDuplicateCheck', () => {
  it('matches on normalized url, ignoring trailing slash and query order', () => {
    // ARRANGE
    const bookmarks = ref([
      bookmark('b-1', 'https://example.org/docs/?b=2&a=1', 'Docs'),
      bookmark('b-2', 'https://other.org/', 'Other'),
    ])

    // ACT
    const { duplicates } = useDuplicateCheck(
      ref('https://example.org/docs?a=1&b=2'),
      bookmarks,
    )

    // ASSERT
    expect(duplicates.value.map((d) => d.id)).toEqual(['b-1'])
  })

  it('reports nothing exact when the caller tracks only the url', () => {
    // ARRANGE — SaveView's usage: no title/folder passed in
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home')])

    // ACT
    const { duplicates, exactDuplicate } = useDuplicateCheck(
      ref('https://example.org/'),
      bookmarks,
    )

    // ASSERT
    expect(duplicates.value).toHaveLength(1)
    expect(duplicates.value[0]!.isExact).toBe(false)
    expect(exactDuplicate.value).toBeNull()
  })

  it('flags the accidental replay: same url, title and folder', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', INBOX.id)])

    // ACT
    const { exactDuplicate } = useDuplicateCheck(ref('https://example.org/'), bookmarks, {
      folders: ref([INBOX]),
      title: ref('Home'),
      folderId: ref(INBOX.id),
    })

    // ASSERT
    expect(exactDuplicate.value?.id).toBe('b-1')
    expect(exactDuplicate.value?.folderName).toBe('Inbox')
  })

  it('treats a differing title as a legitimate second entry', () => {
    // ARRANGE — correcting a just-saved bookmark by filing it under a better name
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Portal', INBOX.id)])

    // ACT
    const { duplicates, exactDuplicate } = useDuplicateCheck(
      ref('https://example.org/'),
      bookmarks,
      { folders: ref([INBOX]), title: ref('Portal - LOCAL'), folderId: ref(INBOX.id) },
    )

    // ASSERT
    expect(duplicates.value).toHaveLength(1)
    expect(exactDuplicate.value).toBeNull()
  })

  it('treats the same entry in another folder as a legitimate second entry', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', ARCHIVE.id)])

    // ACT
    const { duplicates, exactDuplicate } = useDuplicateCheck(
      ref('https://example.org/'),
      bookmarks,
      { folders: ref([INBOX, ARCHIVE]), title: ref('Home'), folderId: ref(INBOX.id) },
    )

    // ASSERT
    expect(duplicates.value[0]!.folderName).toBe('Archive')
    expect(exactDuplicate.value).toBeNull()
  })

  it('matches unfiled against unfiled however the form spells it', () => {
    // ARRANGE — FolderSelectLw yields undefined for "no folder", the wire uses null
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', null)])

    // ACT
    const { exactDuplicate } = useDuplicateCheck(ref('https://example.org/'), bookmarks, {
      title: ref('Home'),
      folderId: ref(undefined),
    })

    // ASSERT
    expect(exactDuplicate.value?.id).toBe('b-1')
  })

  it('ignores surrounding whitespace when comparing titles', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', null)])

    // ACT
    const { exactDuplicate } = useDuplicateCheck(ref('https://example.org/'), bookmarks, {
      title: ref('  Home  '),
      folderId: ref(null),
    })

    // ASSERT
    expect(exactDuplicate.value?.id).toBe('b-1')
  })

  it('excludes the bookmark being edited so editing never warns about itself', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', null)])

    // ACT
    const { duplicates, exactDuplicate } = useDuplicateCheck(
      ref('https://example.org/'),
      bookmarks,
      { excludeBookmarkId: ref('b-1'), title: ref('Home'), folderId: ref(null) },
    )

    // ASSERT
    expect(duplicates.value).toEqual([])
    expect(exactDuplicate.value).toBeNull()
  })

  it('reacts to the folder the form is targeting', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home', INBOX.id)])
    const folderId = ref<string | null>(ARCHIVE.id)
    const { exactDuplicate } = useDuplicateCheck(ref('https://example.org/'), bookmarks, {
      folders: ref([INBOX, ARCHIVE]),
      title: ref('Home'),
      folderId,
    })
    expect(exactDuplicate.value).toBeNull()

    // ACT — user switches the target folder to the one that already holds it
    folderId.value = INBOX.id

    // ASSERT
    expect(exactDuplicate.value?.id).toBe('b-1')
  })

  it('returns no duplicates while the url field is still empty', () => {
    // ARRANGE
    const bookmarks = ref([bookmark('b-1', 'https://example.org/', 'Home')])

    // ACT
    const { duplicates } = useDuplicateCheck(ref(''), bookmarks, { title: ref('Home') })

    // ASSERT
    expect(duplicates.value).toEqual([])
  })
})
