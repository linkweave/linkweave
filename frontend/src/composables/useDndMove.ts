import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { useI18n } from 'vue-i18n'

export function useDndMove() {
  const bookmarkStore = useBookmarkStore()
  const folderStore = useFolderStore()
  const collectionStore = useCollectionStore()
  const notification = useNotificationStore()
  const { t } = useI18n()

  async function moveBookmarkWithUndo(bookmarkId: string, targetFolderId: string | undefined) {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) return
    const previousFolderId = bookmarkStore.bookmarks.find(b => b.id === bookmarkId)?.data.folderId
    if (previousFolderId === targetFolderId) return
    try {
      await bookmarkStore.moveBookmarkToFolder(bookmarkId, { collectionId, folderId: targetFolderId })
      notification.successWithUndo(t('dnd.bookmarkMoved'), t('common.undo'), async () => {
        await bookmarkStore.moveBookmarkToFolder(bookmarkId, { collectionId, folderId: previousFolderId })
      })
    } catch {
      notification.error(t('bookmark.moveError'))
    }
  }

  async function moveFolderWithUndo(folderId: string, targetParentId: string | undefined) {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) return
    const previousParentId = folderStore.folders.find(f => f.id === folderId)?.data.parentId
    try {
      await folderStore.moveFolder(folderId, { collectionId, parentId: targetParentId })
      notification.successWithUndo(t('dnd.folderMoved'), t('common.undo'), async () => {
        await folderStore.moveFolder(folderId, { collectionId, parentId: previousParentId })
      })
    } catch {
      notification.error(t('folder.moveError'))
    }
  }

  return { moveBookmarkWithUndo, moveFolderWithUndo }
}
