import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import type { BookmarkPositionJson, FolderPositionJson } from '@/api/generated'
import { Placement } from '@/api/generated'
import { byManualBookmarkOrder } from '@/utils/bookmarkSort'
import { useI18n } from 'vue-i18n'

export function useDndMove() {
  const bookmarkStore = useBookmarkStore()
  const folderStore = useFolderStore()
  const collectionStore = useCollectionStore()
  const notification = useNotificationStore()
  const { t } = useI18n()

  // Where the bookmark currently sits, expressed as an anchor among its folder
  // group — captured before a move so undo can restore folder AND position
  // (UC-103 A4). Mirrors folderPositionSnapshot below.
  function bookmarkPositionSnapshot(bookmarkId: string): {
    folderId: string | undefined
    position: BookmarkPositionJson | undefined
  } {
    const folderId = bookmarkStore.bookmarks.find(b => b.id === bookmarkId)?.data.folderId
    const siblings = bookmarkStore.bookmarks
      .filter(b => b.data.folderId === folderId)
      .sort(byManualBookmarkOrder)
    const index = siblings.findIndex(b => b.id === bookmarkId)
    const next = siblings[index + 1]
    if (next) return { folderId, position: { anchorBookmarkId: next.id, placement: Placement.Before } }
    const previous = index > 0 ? siblings[index - 1] : undefined
    if (previous) {
      return { folderId, position: { anchorBookmarkId: previous.id, placement: Placement.After } }
    }
    // Only bookmark in its group: any position within the group is the same position.
    return { folderId, position: undefined }
  }

  /** Returns true when the bookmark actually moved (drives landing feedback). */
  async function moveBookmarkWithUndo(
    bookmarkId: string,
    targetFolderId: string | undefined,
    position?: BookmarkPositionJson,
  ): Promise<boolean> {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) return false
    const previous = bookmarkPositionSnapshot(bookmarkId)
    // A folder-only drop onto the bookmark's current folder is a no-op; with an
    // explicit position the drop is a reorder within that folder and proceeds.
    if (previous.folderId === targetFolderId && !position) return false
    try {
      await bookmarkStore.moveBookmarkToFolder(bookmarkId, {
        collectionId,
        folderId: targetFolderId,
        position,
      })
      notification.successWithUndo(t('dnd.bookmarkMoved'), t('common.undo'), async () => {
        await bookmarkStore.moveBookmarkToFolder(bookmarkId, {
          collectionId,
          folderId: previous.folderId,
          // Restore the exact slot only when the drop was position-assigning;
          // a plain move keeps the number (BR-195), so undo needs none either.
          position: position ? previous.position : undefined,
        })
      })
      return true
    } catch {
      notification.error(t('bookmark.moveError'))
      return false
    }
  }

  // Where the folder currently sits, expressed as an anchor among its siblings —
  // captured before a move so undo can restore parent AND position (UC-102 A3).
  function folderPositionSnapshot(folderId: string): {
    parentId: string | undefined
    position: FolderPositionJson | undefined
  } {
    const parentId = folderStore.folders.find(f => f.id === folderId)?.data.parentId
    // folderStore.folders is already in manual order, so filtering keeps it.
    const siblings = folderStore.folders.filter(f => f.data.parentId === parentId)
    const index = siblings.findIndex(f => f.id === folderId)
    const next = siblings[index + 1]
    if (next) return { parentId, position: { anchorFolderId: next.id, placement: Placement.Before } }
    const previous = index > 0 ? siblings[index - 1] : undefined
    if (previous) {
      return { parentId, position: { anchorFolderId: previous.id, placement: Placement.After } }
    }
    // Only child: any position within the group is the same position.
    return { parentId, position: undefined }
  }

  /** Returns true when the folder actually moved (drives landing feedback). */
  async function moveFolderWithUndo(
    folderId: string,
    targetParentId: string | undefined,
    position?: FolderPositionJson,
  ): Promise<boolean> {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) return false
    const previous = folderPositionSnapshot(folderId)
    try {
      await folderStore.moveFolder(folderId, { collectionId, parentId: targetParentId, position })
      notification.successWithUndo(t('dnd.folderMoved'), t('common.undo'), async () => {
        await folderStore.moveFolder(folderId, {
          collectionId,
          parentId: previous.parentId,
          position: previous.position,
        })
      })
      return true
    } catch {
      notification.error(t('folder.moveError'))
      return false
    }
  }

  return { moveBookmarkWithUndo, moveFolderWithUndo }
}
