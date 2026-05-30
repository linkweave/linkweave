import {config} from '@/api'
import type {CollectionSettingsJson} from '@/api/generated'
import {CollectionResourceApi} from '@/api/generated'
import {useNotificationStore} from '@/stores/notification'
import type {Ref, WatchSource} from 'vue'
import {watch} from 'vue'

const collectionApi = new CollectionResourceApi(config)
const SETTINGS_DEBOUNCE_MS = 400

/**
 * Debounced, retry-on-overlap writer for a collection's settings.
 *
 * After construction this composable is the sole writer of `settings`: it mutates
 * the ref optimistically on each `updateSettings` call and again with the canonical
 * response from the server. Callers must not write `settings` directly while a
 * write may be in flight (e.g. don't overwrite it from a parallel GET) — doing so
 * can clobber the optimistic value before the server reply lands.
 *
 * Internal state machine (all three are module-private to this closure):
 *   - settingsFlushTimer  set  ⇔ pendingPatch !== null AND no PUT in flight
 *   - inFlight                 ⇒ pendingPatch was just snapshotted into the request
 *                                and reset to null; further `updateSettings` calls
 *                                during the request repopulate it
 *   - pendingPatch !== null after a PUT returns ⇒ schedule a follow-up flush so the
 *                                later edits don't get lost
 *
 * Flushes also fire eagerly when `currentCollectionId` changes, so navigating away
 * persists in-progress edits instead of waiting out the debounce.
 */
export function useCollectionSettingsWriter(
  settings: Ref<CollectionSettingsJson | null>,
  currentCollectionId: WatchSource<string | null>,
) {
  let settingsFlushTimer: ReturnType<typeof setTimeout> | null = null
  let pendingPatch: { collectionId: string; patch: CollectionSettingsJson } | null = null
  let inFlight = false

  function hasPendingPatch(): boolean {
    return pendingPatch !== null
  }

  async function flushSettings(): Promise<void> {
    if (!pendingPatch || inFlight) return
    const {collectionId, patch} = pendingPatch
    pendingPatch = null
    inFlight = true
    try {
      const result = await collectionApi.apiCollectionsIdSettingsPut({
        id: collectionId,
        collectionSettingsJson: patch,
      })
      if (!hasPendingPatch()) {
        settings.value = result
      }
    } catch (err) {
      console.error('Failed to update collection settings:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to update collection settings')
    } finally {
      inFlight = false
      if (hasPendingPatch()) {
        void flushSettings()
      }
    }
  }

  function updateSettings(collectionId: string, patch: CollectionSettingsJson): void {
    settings.value = {...settings.value, ...patch}
    pendingPatch = {
      collectionId,
      patch: {...(pendingPatch?.collectionId === collectionId ? pendingPatch.patch : {}), ...patch},
    }
    if (settingsFlushTimer) clearTimeout(settingsFlushTimer)
    settingsFlushTimer = setTimeout(() => {
      settingsFlushTimer = null
      void flushSettings()
    }, SETTINGS_DEBOUNCE_MS)
  }

  watch(currentCollectionId, (_id, prevId) => {
    if (prevId && hasPendingPatch()) {
      if (settingsFlushTimer) {
        clearTimeout(settingsFlushTimer)
        settingsFlushTimer = null
      }
      void flushSettings()
    }
  })

  return {updateSettings}
}
