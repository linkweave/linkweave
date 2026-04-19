<script setup lang="ts">
import {ButtonCl} from '@/components/ui'
import {cn} from '@/lib/utils'
import {Menu, X} from 'lucide-vue-next'
import {ref} from 'vue'
import SidebarCl from './SidebarCl.vue'
import HeaderCl from './HeaderCl.vue'
import CollectionSwitcher from './CollectionSwitcher.vue'

const sidebarOpen = ref(false)

const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

const closeSidebar = () => {
  sidebarOpen.value = false
}
</script>

<template>
  <div class="flex flex-col h-screen bg-background">
    <HeaderCl>
      <template #leading>
        <ButtonCl variant="ghost" size="icon" class="lg:hidden" data-testid="mobile-sidebar-toggle" @click="toggleSidebar">
          <Menu class="h-5 w-5" />
        </ButtonCl>
      </template>
      <template #title>
        <slot name="header-title">
          <CollectionSwitcher />
        </slot>
      </template>
      <template #actions>
        <slot name="header-actions" />
      </template>
    </HeaderCl>

    <!-- Body: Sidebar + Content -->
    <div class="flex flex-1 min-h-0">
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
            'fixed inset-y-0 left-0 z-50 w-60 bg-background border-r border-border transform transition-transform lg:relative lg:translate-x-0 flex flex-col',
            sidebarOpen ? 'translate-x-0' : '-translate-x-full'
          )
        "
      >
        <div class="flex items-center justify-between p-4 lg:hidden shrink-0">
          <span class="font-semibold">Menu</span>
          <ButtonCl variant="ghost" size="icon" @click="closeSidebar">
            <X class="h-5 w-5" />
          </ButtonCl>
        </div>
        <SidebarCl class="flex-1 min-h-0" />
      </aside>

      <!-- Main content -->
      <main class="flex-1 overflow-y-auto p-6">
        <slot />
      </main>
    </div>
  </div>
</template>
