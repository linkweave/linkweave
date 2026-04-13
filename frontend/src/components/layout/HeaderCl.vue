<script setup lang="ts">
import { UserMenuCl } from '@/components/ui'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/logo.png'
import CollectionSwitcher from './CollectionSwitcher.vue'

const auth = useAuthStore()
</script>

<template>
  <header class="relative z-[60] flex items-center justify-between gap-4 p-4 border-b border-border bg-card shrink-0">
    <div class="flex items-center gap-3">
      <slot name="leading" />
      <router-link to="/" class="shrink-0 cursor-pointer">
        <img :src="logoUrl" alt="" class="h-6 w-6" />
      </router-link>
      <slot name="title">
        <CollectionSwitcher />
      </slot>
    </div>
    <div class="flex items-center gap-2">
      <slot name="actions" />
      <UserMenuCl v-if="auth.isAuthenticated" />
    </div>
  </header>
</template>
