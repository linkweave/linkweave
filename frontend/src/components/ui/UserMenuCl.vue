<script setup lang="ts">
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from 'radix-vue'
import { ChevronDown, LogOut, Settings, Trash2, Sparkles } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
import { useCollectionStore } from '@/stores/collection'
import { useTrashbinStore } from '@/stores/trashbin'
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
    <DropdownMenuPortal>
      <DropdownMenuContent
        class="min-w-[160px] z-[100] rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
        align="end"
        :side-offset="4"
      >
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
        </div>
        <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />
        <DropdownMenuItem
          class="relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
          data-testid="user-menu-cleanup-suggestions"
          @select="openCleanupSuggestions"
        >
          <Sparkles class="h-4 w-4" />
          <span class="flex-1">{{ $t('header.cleanupSuggestions') }}</span>
        </DropdownMenuItem>
        <DropdownMenuItem
          class="relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
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
        </DropdownMenuItem>
        <DropdownMenuItem
          class="relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
          @select="isSettingsOpen = true"
        >
          <Settings class="h-4 w-4" />
          {{ $t('header.settings') }}
        </DropdownMenuItem>
        <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />
        <DropdownMenuItem
          class="relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
          @select="auth.logout"
        >
          <LogOut class="h-4 w-4" />
          {{ $t('header.logout') }}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>

  <SettingsDialog v-model:open="isSettingsOpen" />
</template>
