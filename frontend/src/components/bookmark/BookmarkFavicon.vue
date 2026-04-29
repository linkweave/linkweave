<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useCollectionStore } from '@/stores/collection'
import { hostnameOf, matchesAllowlist, parseAllowlist } from '@/lib/favicon-allowlist'

const props = defineProps<{
  bookmarkId: string
  url: string
  size?: number
}>()

const collectionStore = useCollectionStore()
const failed = ref(false)

watch(() => [props.bookmarkId, props.url], () => { failed.value = false })

const allowlist = computed(() => parseAllowlist(collectionStore.collectionInfo?.faviconAllowlist))

const src = computed<string | null>(() => {
  const host = hostnameOf(props.url)
  if (!host) return null
  if (matchesAllowlist(host, allowlist.value)) {
    return `https://${host}/favicon.ico`
  }
  const cid = collectionStore.currentCollectionId
  if (!cid) return null
  return `/api/collections/${encodeURIComponent(cid)}/bookmarks/${encodeURIComponent(props.bookmarkId)}/favicon`
})

const px = computed(() => `${props.size ?? 16}px`)
</script>

<template>
  <img
    v-if="src && !failed"
    :src="src"
    alt=""
    loading="lazy"
    referrerpolicy="no-referrer"
    :style="{ width: px, height: px }"
    class="rounded-sm shrink-0"
    @error="failed = true"
  />
</template>
