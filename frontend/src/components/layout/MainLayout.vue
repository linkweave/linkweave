<script setup lang="ts">
import {Button, LanguageSwitcher} from '@/components/ui'
import {cn} from '@/lib/utils'
import {LogOut, Menu, X} from 'lucide-vue-next'
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Sidebar from './Sidebar.vue'
import {useAuthStore} from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
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
    <!-- Header (full width) -->
    <header class="flex items-center justify-between gap-4 p-4 border-b border-border bg-card shrink-0">
      <div class="flex items-center gap-3">
        <Button variant="ghost" size="icon" class="lg:hidden" @click="toggleSidebar">
          <Menu class="h-5 w-5" />
        </Button>
        <h1 class="text-xl font-semibold text-foreground">{{ t('app.title') }}</h1>
      </div>
      <div class="flex items-center gap-2">
        <slot name="header-actions" />
        <span v-if="auth.isAuthenticated" class="text-sm text-muted-foreground">{{ auth.displayName }}</span>
        <Button v-if="auth.isAuthenticated" variant="ghost" size="icon" @click="auth.logout">
          <LogOut class="h-4 w-4" />
        </Button>
        <LanguageSwitcher />
      </div>
    </header>

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
      <main class="flex-1 overflow-y-auto p-6">
        <slot />
      </main>
    </div>
  </div>
</template>
