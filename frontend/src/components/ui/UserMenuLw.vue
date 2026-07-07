<script setup lang="ts">
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from 'radix-vue'
import DropdownMenuContentLw from '@/components/ui/DropdownMenuContentLw.vue'
import DropdownMenuItemLw from '@/components/ui/DropdownMenuItemLw.vue'
import { ChevronDown, LogOut, Settings, Shield, Trash2, Sparkles } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
import { useCollectionStore } from '@/stores/collection'
import { useTrashbinStore } from '@/stores/trashbin'
import { Permission } from '@/api/generated'
import type { SupportedLocale } from '@/i18n'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import SettingsDialog from '@/components/ui/SettingsDialog.vue'
import { avatarColor } from '@/composables/useAvatarColor'

const auth = useAuthStore()
const localeStore = useLocaleStore()
const collectionStore = useCollectionStore()
const trashbinStore = useTrashbinStore()
const routerInstance = useRouter()

const isSettingsOpen = ref(false)
const avatarBg = computed(() => avatarColor(auth.displayName))
const canAccessAdmin = computed(() => !!auth.user?.permissions.has(Permission.Support))

onMounted(() => {
  trashbinStore.refreshCount().catch(() => {})
})

function openTrashbin() {
  routerInstance.push({ name: 'trashbin' })
}

function openCleanupSuggestions() {
  const currentRoute = routerInstance.currentRoute.value
  const collectionId = (currentRoute.params.id as string) || collectionStore.currentCollectionId
  if (collectionId) {
    routerInstance.push({ name: 'cleanup-suggestions', query: { collectionId } })
  }
}

function openAdminUsers() {
  routerInstance.push({ name: 'admin-users' })
}

function switchLocale(locale: SupportedLocale) {
  localeStore.setLocale(locale)
}
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button
        data-testid="user-menu-trigger"
        class="inline-flex items-center gap-1.5 rounded-md text-sm font-medium text-foreground transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      >
        <!-- Mobile: round avatar with first initial -->
        <span class="sm:hidden h-9 w-9 rounded-full text-white flex items-center justify-center text-sm font-semibold select-none" :style="{ backgroundColor: avatarBg }">
          {{ auth.displayName.charAt(0).toUpperCase() }}
        </span>
        <!-- sm+: full name with chevron -->
        <span class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5">
          {{ auth.displayName }}
          <ChevronDown class="h-4 w-4" />
        </span>
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuContentLw class="min-w-[160px] z-[100]">
        <div class="sm:hidden px-2 py-1.5 text-sm font-medium text-foreground truncate">
          {{ auth.displayName }}
        </div>
        <DropdownMenuSeparator class="sm:hidden -mx-1 my-1 h-px bg-border" />
        <div class="flex items-center justify-center gap-1 rounded-sm px-2 py-1.5 text-sm">
          <button
            class="px-1 transition-colors hover:text-accent-foreground"
            :class="localeStore.currentLocale === 'de' ? 'underline font-semibold text-accent-foreground' : 'text-muted-foreground'"
            @click="switchLocale('de')"
          >
            DE
          </button>
          <span class="text-muted-foreground">|</span>
          <button
            class="px-1 transition-colors hover:text-accent-foreground"
            :class="localeStore.currentLocale === 'en' ? 'underline font-semibold text-accent-foreground' : 'text-muted-foreground'"
            @click="switchLocale('en')"
          >
            EN
          </button>
          <span class="text-muted-foreground">|</span>
          <button
            class="px-1 transition-colors hover:text-accent-foreground"
            :class="localeStore.currentLocale === 'fr' ? 'underline font-semibold text-accent-foreground' : 'text-muted-foreground'"
            @click="switchLocale('fr')"
          >
            FR
          </button>
        </div>
        <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />
        <DropdownMenuItemLw
          v-if="canAccessAdmin"
          data-testid="user-menu-admin-users"
          @select="openAdminUsers"
        >
          <Shield class="h-4 w-4" />
          <span class="flex-1">{{ $t('header.adminUsers') }}</span>
        </DropdownMenuItemLw>
        <DropdownMenuItemLw
          data-testid="user-menu-cleanup-suggestions"
          @select="openCleanupSuggestions"
        >
          <Sparkles class="h-4 w-4" />
          <span class="flex-1">{{ $t('header.cleanupSuggestions') }}</span>
        </DropdownMenuItemLw>
        <DropdownMenuItemLw
          data-testid="user-menu-trashbin"
          @select="openTrashbin"
        >
          <Trash2 class="h-4 w-4" />
          <span class="flex-1">{{ $t('header.trashbin') }}</span>
          <span
            v-if="trashbinStore.count > 0"
            class="ml-auto inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-muted px-1.5 text-xs font-medium text-muted-foreground"
          >
            {{ trashbinStore.count }}
          </span>
        </DropdownMenuItemLw>
        <DropdownMenuItemLw
          @select="isSettingsOpen = true"
        >
          <Settings class="h-4 w-4" />
          {{ $t('header.settings') }}
        </DropdownMenuItemLw>
        <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />
        <DropdownMenuItemLw
          @select="auth.logout"
        >
          <LogOut class="h-4 w-4" />
          {{ $t('header.logout') }}
        </DropdownMenuItemLw>
    </DropdownMenuContentLw>
  </DropdownMenuRoot>

  <SettingsDialog v-model:open="isSettingsOpen" />
</template>
