<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useCollectionStore } from '@/stores/collection'
import { hostnameOf, matchesAllowlist, parseAllowlist } from '@/lib/favicon-allowlist'
import { Globe } from 'lucide-vue-next'

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

// Defer the <img> element's mount to browser idle time. Mounting an <img>
// (even with loading="lazy") triggers synchronous Safari work — URL parsing,
// image element init, and on failure the onerror cascade. With hundreds of
// rows in the grouped layout that compounds to ~7ms/row of mount cost. By
// rendering the Globe placeholder synchronously and only swapping to <img>
// during requestIdleCallback, the layout-flip stays fast and favicons fade
// in shortly after.
const imgReady = ref(false)
let idleHandle: number | null = null

onMounted(() => {
  const cb = () => { imgReady.value = true; idleHandle = null }
  if (typeof globalThis.requestIdleCallback === 'function') {
    idleHandle = globalThis.requestIdleCallback(cb, { timeout: 500 })
  } else {
    // Safari < 26 / older browsers without requestIdleCallback.
    idleHandle = globalThis.setTimeout(cb, 50)
  }
})

onBeforeUnmount(() => {
  if (idleHandle == null) return
  if (typeof globalThis.cancelIdleCallback === 'function') {
    globalThis.cancelIdleCallback(idleHandle)
  } else {
    clearTimeout(idleHandle)
  }
})
</script>

<template>
  <img
    v-if="imgReady && src && !failed"
    :src="src"
    alt=""
    loading="lazy"
    referrerpolicy="no-referrer"
    :style="{ width: px, height: px }"
    class="rounded-sm shrink-0"
    @error="failed = true"
  />
  <Globe
    v-else
    :size="size ?? 16"
    class="shrink-0 text-muted-foreground"
  />
</template>
