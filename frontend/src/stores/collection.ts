import { config } from '@/api'
import type {
  CollectionInfoJson,
  CollectionMemberJson,
  CollectionSettingsJson,
  CollectionSummaryJson,
} from '@/api/generated'
import { CollectionResourceApi, CollectionRole } from '@/api/generated'
import { useCollectionSettingsWriter } from '@/composables/useCollectionSettingsWriter'
import * as offlineCache from '@/lib/offline-cache'
import { navigate } from '@/lib/routerNavigation'
import { registerStoreReset } from '@/lib/storeReset'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { SYSTEM_DEFAULT_SORT } from '@/utils/bookmarkSort'
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

const collectionApi = new CollectionResourceApi(config)

export const useCollectionStore = defineStore('collection', () => {
  const currentCollectionId = ref<string | null>(null)
  const collectionInfo = ref<CollectionInfoJson | null>(null)
  const collections = ref<CollectionSummaryJson[]>([])
  const settings = ref<CollectionSettingsJson | null>(null)
  const loading = ref(false)
  const searchQuery = ref('')

  const collectionName = computed(() => collectionInfo.value?.name ?? null)

  const settingsLayout = computed<'list' | 'grid' | 'grouped' | null>(() => {
    const v = settings.value?.layout
    return v === 'list' || v === 'grid' || v === 'grouped' ? v : null
  })

  // Two primitive computeds (rather than a packed {field, direction} object)
  // so that unrelated settings changes — e.g. flipping layout — don't
  // invalidate downstream computeds that depend on the sort.
  const sortField = computed(() => settings.value?.sortField ?? SYSTEM_DEFAULT_SORT.field)
  const sortDirection = computed(
    () => settings.value?.sortDirection ?? SYSTEM_DEFAULT_SORT.direction,
  )

  const hasSortOverride = computed(
    () => settings.value?.sortField != null || settings.value?.sortDirection != null,
  )

  async function resetSortPreference(collectionId: string) {
    try {
      await collectionApi.apiCollectionsIdSettingsSortDelete({ id: collectionId })
      if (settings.value) {
        settings.value = { ...settings.value, sortField: undefined, sortDirection: undefined }
      }
    } catch (err) {
      console.error('Failed to reset sort preference:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to reset sort preference')
    }
  }
  const defaultCollectionId = computed(() => collections.value.find((c) => c.isDefault)?.id ?? null)

  const filteredCollections = computed(() => {
    if (!searchQuery.value) return collections.value
    const query = searchQuery.value.toLowerCase()
    return collections.value.filter((c) => c.name?.toLowerCase().includes(query))
  })

  const collectionsFetched = ref(false)

  function setCurrentCollectionId(id: string | null) {
    currentCollectionId.value = id
  }

  async function fetchCollections() {
    try {
      collections.value = (await collectionApi.apiCollectionsGet()).collections
      const auth = useAuthStore()
      if (auth.user?.email) {
        offlineCache
          .saveCollections(auth.user.email, collections.value)
          .catch((err) => console.error('Failed to cache collections for offline use:', err))
      }
    } catch (err) {
      console.error('Failed to fetch collections:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load collections')
    }
  }

  async function fetchCollectionInfo(collectionId: string) {
    if (!collectionId) {
      collectionInfo.value = null
      settings.value = null
      return
    }

    loading.value = true
    try {
      const [info, fetchedSettings] = await Promise.all([
        collectionApi.apiCollectionsIdGet({ id: collectionId }),
        collectionApi.apiCollectionsIdSettingsGet({ id: collectionId }).catch(() => null),
      ])
      collectionInfo.value = info
      settings.value = fetchedSettings
      const auth = useAuthStore()
      if (auth.user?.email && collectionInfo.value) {
        offlineCache
          .saveCollectionInfo(auth.user.email, collectionInfo.value)
          .catch((err) => console.error('Failed to cache collection info for offline use:', err))
      }
    } catch (err) {
      console.error('Failed to fetch collection info:', err)
      collectionInfo.value = null
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load collection')
    } finally {
      loading.value = false
    }
  }

  async function setDefaultCollection(collectionId: string) {
    try {
      await collectionApi.apiCollectionsIdDefaultPut({ id: collectionId })
      collections.value = collections.value.map((c) => ({
        ...c,
        isDefault: c.id === collectionId,
      }))

      const auth = useAuthStore()
      auth.updateDefaultCollectionId(collectionId)
    } catch (err) {
      console.error('Failed to set default collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to set default collection')
    }
  }

  async function createCollection(name: string): Promise<CollectionSummaryJson | null> {
    try {
      const result = await collectionApi.apiCollectionsPost({ collectionCreateJson: { name } })
      await fetchCollections()
      return result
    } catch (err) {
      console.error('Failed to create collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to create collection')
      return null
    }
  }

  async function updateCollection(
    collectionId: string,
    name: string,
    browserFetchAllowlist?: string,
    screenshotEnabled = false,
  ): Promise<boolean> {
    try {
      await collectionApi.apiCollectionsIdPut({
        id: collectionId,
        collectionUpdateJson: { name, browserFetchAllowlist, screenshotEnabled },
      })
      await fetchCollections()
      if (currentCollectionId.value === collectionId) {
        await fetchCollectionInfo(collectionId)
      }
      return true
    } catch (err) {
      console.error('Failed to update collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to update collection')
      return false
    }
  }

  async function deleteCollection(collectionId: string): Promise<boolean> {
    try {
      await collectionApi.apiCollectionsIdDelete({ id: collectionId })
      const wasCurrentCollection = currentCollectionId.value === collectionId
      if (wasCurrentCollection) {
        currentCollectionId.value = null
      }
      await fetchCollections()
      if (wasCurrentCollection) {
        const fallback = collections.value.find((c) => c.isDefault) ?? collections.value[0]
        if (fallback?.id) {
          switchCollection(fallback.id)
        }
      }
      return true
    } catch (err) {
      console.error('Failed to delete collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to delete collection')
      return false
    }
  }

  function switchCollection(collectionId: string) {
    setCurrentCollectionId(collectionId)
    navigate({ name: 'collection', params: { id: collectionId } })
  }

  const { updateSettings } = useCollectionSettingsWriter(settings, () => currentCollectionId.value)

  watch(
    currentCollectionId,
    (id, _prevId) => {
      if (id) {
        fetchCollectionInfo(id)
        if (!collectionsFetched.value) {
          collectionsFetched.value = true
          fetchCollections()
        }
      } else {
        collectionInfo.value = null
      }
    },
    { immediate: true },
  )

  async function fetchMembers(collectionId: string): Promise<CollectionMemberJson[]> {
    try {
      return (await collectionApi.apiCollectionsIdMembersGet({ id: collectionId })).members
    } catch (err) {
      console.error('Failed to fetch members:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load members')
      return []
    }
  }

  async function shareWithUser(
    collectionId: string,
    email: string,
    role?: CollectionRole,
  ): Promise<CollectionMemberJson | null> {
    try {
      return await collectionApi.apiCollectionsIdMembersPost({
        id: collectionId,
        collectionShareJson: { email, role },
      })
    } catch (err) {
      console.error('Failed to share collection:', err)
      throw err
    }
  }

  async function revokeAccess(collectionId: string, userId: string): Promise<void> {
    try {
      await collectionApi.apiCollectionsIdMembersUserIdDelete({ id: collectionId, userId })
    } catch (err) {
      console.error('Failed to revoke access:', err)
      throw err
    }
  }

  async function changeMemberRole(
    collectionId: string,
    userId: string,
    role: CollectionRole,
  ): Promise<CollectionMemberJson | null> {
    try {
      return await collectionApi.apiCollectionsIdMembersUserIdPut({
        id: collectionId,
        userId,
        collectionMemberRoleJson: { role },
      })
    } catch (err) {
      console.error('Failed to change member role:', err)
      throw err
    }
  }

  // Update the cached role for a collection without a full refetch — used after a
  // self-step-down so the UI immediately reflects the viewer's lost privileges.
  function setCollectionRole(collectionId: string, role: CollectionRole) {
    collections.value = collections.value.map((c) => (c.id === collectionId ? { ...c, role } : c))
  }

  // The viewer's own role on a collection — single source of truth for the
  // permission-gating helpers below (avoids each component re-deriving it).
  function roleFor(collectionId: string | null | undefined): CollectionRole | undefined {
    if (!collectionId) return undefined
    return collections.value.find((c) => c.id === collectionId)?.role
  }

  function isCollectionOwner(collectionId: string | null | undefined): boolean {
    return roleFor(collectionId) === CollectionRole.Owner
  }

  // Owner or admin — the roles allowed to manage members and collection settings.
  function canManageCollection(collectionId: string | null | undefined): boolean {
    const role = roleFor(collectionId)
    return role === CollectionRole.Owner || role === CollectionRole.Admin
  }

  function reset() {
    currentCollectionId.value = null
    collectionInfo.value = null
    collections.value = []
    collectionsFetched.value = false
  }
  registerStoreReset(reset)

  return {
    currentCollectionId,
    collectionInfo,
    collections,
    settings,
    settingsLayout,
    sortField,
    sortDirection,
    hasSortOverride,
    updateSettings,
    resetSortPreference,
    loading,
    searchQuery,
    collectionName,
    defaultCollectionId,
    filteredCollections,
    setCurrentCollectionId,
    fetchCollectionInfo,
    fetchCollections,
    setDefaultCollection,
    createCollection,
    updateCollection,
    deleteCollection,
    switchCollection,
    fetchMembers,
    shareWithUser,
    revokeAccess,
    changeMemberRole,
    setCollectionRole,
    roleFor,
    isCollectionOwner,
    canManageCollection,
    collectionsFetched,
  }
})
