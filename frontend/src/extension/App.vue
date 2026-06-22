<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useExtensionStore } from './stores/extension'
import LoginPrompt from './views/LoginPrompt.vue'
import SaveView from './views/SaveView.vue'
import BrowseView from './views/BrowseView.vue'
import SelectLw from '@/components/ui/SelectLw.vue'

const store = useExtensionStore()

type Tab = 'save' | 'browse'
const activeTab = ref<Tab>('save')

// Active tab URL/title from the browser, read once on popup open
const activeTabUrl = ref('')
const activeTabTitle = ref('')
// URL pre-filled via context menu right-click (stored by service worker)
const contextMenuUrl = ref<string | null>(null)

const CONTEXT_MENU_KEY = '_cl_contextMenuUrl'

onMounted(async () => {
  // Read context menu URL if the popup was triggered via right-click
  // Uses chrome.storage.local (not session) for Firefox compatibility
  const stored = await chrome.storage.local.get(CONTEXT_MENU_KEY)
  if (stored[CONTEXT_MENU_KEY]) {
    contextMenuUrl.value = stored[CONTEXT_MENU_KEY] as string
    await chrome.storage.local.remove(CONTEXT_MENU_KEY)
    activeTab.value = 'save'
  }

  // Read the current browser tab's URL and title
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  activeTabUrl.value = tab?.url ?? ''
  activeTabTitle.value = tab?.title ?? ''
  store.currentTabUrl = activeTabUrl.value

  // Load config first so webAppUrl is available even if auth fails
  await store.loadConfig()
  // Initialize auth + collection data
  await store.initialize()
})
</script>

<template>
  <div class="w-[400px] min-h-[480px] max-h-[600px] flex flex-col bg-background text-foreground">
    <!-- Header -->
    <div class="flex items-center gap-2 px-4 py-3 border-b border-border">
      <img src="@/assets/ChainlinkLogoTrResc.png" alt="Chainlink" class="w-5 h-5" />
      <span class="font-semibold text-sm">Chainlink</span>

      <!-- Collection switcher (only if user has multiple collections) -->
      <SelectLw
        v-if="store.isAuthenticated && store.collections.length > 1"
        :model-value="store.currentCollectionId"
        class="ml-auto w-auto"
        @update:model-value="(v) => store.loadCollection(String(v))"
      >
        <option v-for="c in store.collections" :key="c.id" :value="c.id">
          {{ c.name }}
        </option>
      </SelectLw>
    </div>

    <!-- Loading state -->
    <div v-if="store.loading" class="flex-1 flex items-center justify-center">
      <span class="text-sm text-muted-foreground">Loading…</span>
    </div>

    <!-- Not authenticated -->
    <template v-else-if="!store.isAuthenticated">
      <LoginPrompt />
      <div class="px-4 pb-3 flex flex-col gap-2">
        <button
          class="w-full text-xs py-1.5 rounded border border-border text-muted-foreground hover:text-foreground hover:border-foreground transition-colors"
          @click="store.initialize()"
        >
          Retry / Re-initialize
        </button>
        <p v-if="store.error" class="text-xs text-destructive text-center">{{ store.error }}</p>
      </div>
    </template>

    <!-- Authenticated -->
    <template v-else>
      <!-- Tab bar -->
      <div class="flex border-b border-border">
        <button
          class="flex-1 py-2 text-xs font-medium transition-colors"
          :class="activeTab === 'save'
            ? 'text-primary border-b-2 border-primary'
            : 'text-muted-foreground hover:text-foreground'"
          @click="activeTab = 'save'"
        >
          Save
        </button>
        <button
          class="flex-1 py-2 text-xs font-medium transition-colors"
          :class="activeTab === 'browse'
            ? 'text-primary border-b-2 border-primary'
            : 'text-muted-foreground hover:text-foreground'"
          @click="activeTab = 'browse'"
        >
          Browse
          <span
            v-if="store.collectionInfo?.bookmarks?.length"
            class="ml-1 text-[10px] text-muted-foreground"
          >
            <template v-if="store.filteredBookmarks.length < store.collectionInfo.bookmarks.length">
              {{ store.filteredBookmarks.length }}/{{ store.collectionInfo.bookmarks.length }}
            </template>
            <template v-else>{{ store.collectionInfo.bookmarks.length }}</template>
          </span>
        </button>
      </div>

      <!-- View content -->
      <div class="flex-1 overflow-y-auto">
        <SaveView
          v-if="activeTab === 'save'"
          :initial-url="contextMenuUrl ?? activeTabUrl"
          :initial-title="contextMenuUrl ? '' : activeTabTitle"
          @saved="activeTab = 'browse'"
          @browse="activeTab = 'browse'"
        />
        <BrowseView v-else />
      </div>
    </template>
  </div>
</template>
