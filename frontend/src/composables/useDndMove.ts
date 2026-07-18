import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import type { FolderPositionJson } from '@/api/generated'
import { Placement } from '@/api/generated'
import { useI18n } from 'vue-i18n'

export function useDndMove() {
  const bookmarkStore = useBookmarkStore()
  const folderStore = useFolderStore()
  const collectionStore = useCollectionStore()
  const notification = useNotificationStore()
  const { t } = useI18n()

  /** Returns true when the bookmark actually moved (drives landing feedback). */
  async function moveBookmarkWithUndo(
    bookmarkId: string,
    targetFolderId: string | undefined,
  ): Promise<boolean> {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) return false
    const previousFolderId = bookmarkStore.bookmarks.find(b => b.id === bookmarkId)?.data.folderId
    if (previousFolderId === targetFolderId) return false
    try {
      await bookmarkStore.moveBookmarkToFolder(bookmarkId, { collectionId, folderId: targetFolderId })
      notification.successWithUndo(t('dnd.bookmarkMoved'), t('common.undo'), async () => {
        await bookmarkStore.moveBookmarkToFolder(bookmarkId, { collectionId, folderId: previousFolderId })
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
