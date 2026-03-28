<script setup lang="ts">
import { ref } from 'vue'
import { Menu, X } from 'lucide-vue-next'
import { Button, LanguageSwitcher } from '@/components/ui'
import Sidebar from './Sidebar.vue'
import { cn } from '@/lib/utils'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const sidebarOpen = ref(false)

const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

const closeSidebar = () => {
  sidebarOpen.value = false
}
</script>

<template>
  <div class="flex h-screen bg-background">
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
          'fixed inset-y-0 left-0 z-50 w-60 bg-background border-r border-border transform transition-transform lg:relative lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )
      "
    >
      <div class="flex items-center justify-between p-4 lg:hidden">
        <span class="font-semibold">Menu</span>
        <Button variant="ghost" size="icon" @click="closeSidebar">
          <X class="h-5 w-5" />
        </Button>
      </div>
      <Sidebar class="h-full" />
    </aside>

    <!-- Main content -->
    <main class="flex-1 flex flex-col min-w-0">
      <!-- Header -->
      <header class="flex items-center justify-between gap-4 p-4 border-b border-border bg-card">
        <div class="flex items-center gap-3">
          <Button variant="ghost" size="icon" class="lg:hidden" @click="toggleSidebar">
            <Menu class="h-5 w-5" />
          </Button>
          <h1 class="text-xl font-semibold text-foreground">{{ t('app.title') }}</h1>
        </div>
        <div class="flex items-center gap-2">
          <slot name="header-actions" />
          <LanguageSwitcher />
        </div>
      </header>

      <!-- Content area -->
      <div class="flex-1 overflow-y-auto p-6">
        <slot />
      </div>
    </main>
  </div>
</template>
