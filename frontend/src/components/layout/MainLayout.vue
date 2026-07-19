<script setup lang="ts">
import { ButtonLw } from '@/components/ui'
import OfflineBanner from '@/components/ui/OfflineBanner.vue'
import { useDndAutoScroll } from '@/composables/useDndAutoScroll'
import { installSessionExpiryWatch } from '@/lib/session-watch'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/auth'
import { useOfflineStore } from '@/stores/offline'
import { Menu, X } from '@lucide/vue'
import { onMounted, ref } from 'vue'
import CollectionSwitcher from './CollectionSwitcher.vue'
import HeaderLw from './HeaderLw.vue'
import SidebarLw from './SidebarLw.vue'

withDefaults(defineProps<{ hideSidebar?: boolean }>(), { hideSidebar: false })

const sidebarOpen = ref(false)
const offline = useOfflineStore()
const auth = useAuthStore()

// Dragging a bookmark near the top/bottom of the content area scrolls it, so a
// Manual-mode reorder (UC-103) can reach rows outside the viewport.
const mainScrollContainer = ref<HTMLElement | null>(null)
useDndAutoScroll(mainScrollContainer)

onMounted(() => {
  offline.setupListeners()
  offline.loadLastSyncTime()
  // MainLayout only wraps authenticated views, so wiring here keeps the
  // probe away from publicly accessible pages (UC-098 A4); isActive guards the rest.
  installSessionExpiryWatch({
    isSessionAvailable: () => auth.isAuthenticated,
    onExpired: auth.handleSessionExpired,
  })
})

const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

const closeSidebar = () => {
  sidebarOpen.value = false
}
</script>

<template>
  <div class="flex flex-col h-screen bg-background">
    <OfflineBanner />
    <HeaderLw>
      <template #leading>
        <slot name="header-leading">
          <ButtonLw
            v-if="!hideSidebar"
            variant="ghost"
            size="icon"
            class="lg:hidden shrink-0"
            data-testid="mobile-sidebar-toggle"
            @click="toggleSidebar"
          >
            <Menu class="h-5 w-5" />
          </ButtonLw>
        </slot>
      </template>
      <template #title>
        <slot name="header-title">
          <CollectionSwitcher />
        </slot>
      </template>
      <template #search>
        <slot name="header-search" />
      </template>
      <template #search-mobile>
        <slot name="header-search-mobile" />
      </template>
      <template #actions>
        <slot name="header-actions" />
      </template>
    </HeaderLw>

    <!-- Body: Sidebar + Content -->
    <div class="flex flex-1 min-h-0">
      <template v-if="!hideSidebar">
        <!-- Mobile sidebar overlay -->
        <div
          v-if="sidebarOpen"
          class="fixed inset-0 bg-background/80 backdrop-blur-sm z-40 lg:hidden"
          @click="closeSidebar"
        />

        <!-- Sidebar -->
        <aside
          :class="
            cn(
              'fixed inset-y-0 left-0 z-50 w-60 bg-sidebar border-r border-border transform transition-transform lg:relative lg:translate-x-0 flex flex-col',
              sidebarOpen ? 'translate-x-0' : '-translate-x-full',
            )
          "
        >
          <div class="flex items-center justify-between p-4 lg:hidden shrink-0">
            <span class="font-semibold">Menu</span>
            <ButtonLw variant="ghost" size="icon" @click="closeSidebar">
              <X class="h-5 w-5" />
            </ButtonLw>
          </div>
          <SidebarLw class="flex-1 min-h-0" />
        </aside>
      </template>

      <!-- Main content -->
      <main ref="mainScrollContainer" class="flex-1 overflow-y-auto">
        <slot name="toolbar" />
        <div class="p-3 sm:p-6">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
