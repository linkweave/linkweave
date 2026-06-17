import { useI18n } from 'vue-i18n'
import { config } from '@/api'
import { ScreenshotResourceApi } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'

const screenshotApi = new ScreenshotResourceApi(config)

// Triggers a server-side re-capture for a bookmark's screenshot (bypasses the
// on-disk cache) and reports success. The caller is responsible for bumping
// its own cache-busting nonce on success so the <img> reloads. Shared by the
// bookmark card's row menu and the hover-preview popup footer (UC-093) so the
// POST + error-handling lives in exactly one place.
export function useScreenshotRefresh() {
  const collectionStore = useCollectionStore()
  const notification = useNotificationStore()
  const { t } = useI18n()

  return async function refreshScreenshot(bookmarkId: string): Promise<boolean> {
    const cid = collectionStore.currentCollectionId
    if (!cid) return false
    try {
      await screenshotApi.apiCollectionsCollectionIdBookmarksBookmarkIdScreenshotRefreshPost({
        collectionId: cid,
        bookmarkId,
      })
      return true
    } catch (err) {
      void notification.handleApiError(err, t('bookmark.refreshPreviewError'))
      return false
    }
  }
}
