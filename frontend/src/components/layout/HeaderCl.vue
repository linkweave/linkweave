<script setup lang="ts">
import { UserMenuCl } from '@/components/ui'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/logo.png'
import CollectionSwitcher from './CollectionSwitcher.vue'

const auth = useAuthStore()
</script>

<template>
  <header class="relative z-[60] flex items-center gap-3 sm:gap-4 p-3 sm:p-4 border-b border-border bg-card shrink-0">
    <!-- Leading: hamburger + logo + collection switcher -->
    <div class="flex items-center gap-2 sm:gap-3 shrink-0">
      <slot name="leading" />
      <router-link to="/" class="shrink-0 cursor-pointer">
        <img :src="logoUrl" alt="" class="h-6 w-6" />
      </router-link>
      <slot name="title">
        <CollectionSwitcher />
      </slot>
    </div>

    <!-- Centre: search (sm tablet+), max-width narrows on tablet, expands on desktop -->
    <div class="hidden sm:flex flex-1 justify-center min-w-0">
      <div class="w-full max-w-[280px] lg:max-w-[560px]">
        <slot name="search" />
      </div>
    </div>

    <!-- Spacer for mobile only (pushes actions to the right) -->
    <div class="flex-1 sm:hidden" />

    <!-- Actions: mobile search icon + other actions + user menu -->
    <div class="flex items-center gap-1 sm:gap-2 shrink-0">
      <slot name="search-mobile" />
      <slot name="actions" />
      <UserMenuCl v-if="auth.isAuthenticated" />
    </div>
  </header>
</template>
