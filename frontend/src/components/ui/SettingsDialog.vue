<script setup lang="ts">
import { DialogCl } from '@/components/ui'
import { useUiStore, type Theme, type BookmarkLayout } from '@/stores/ui'
import { LayoutGrid, LayoutList, Monitor, Moon, Sun, Layers } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ui = useUiStore()

interface Props {
  open?: boolean
}

withDefaults(defineProps<Props>(), {
  open: false,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const themes: { value: Theme; icon: typeof Sun; labelKey: string }[] = [
  { value: 'light', icon: Sun, labelKey: 'settings.themeLight' },
  { value: 'dark', icon: Moon, labelKey: 'settings.themeDark' },
  { value: 'system', icon: Monitor, labelKey: 'settings.themeSystem' },
]

const layouts: { value: BookmarkLayout; icon: typeof LayoutList; labelKey: string }[] = [
  { value: 'list', icon: LayoutList, labelKey: 'settings.layoutList' },
  { value: 'grid', icon: LayoutGrid, labelKey: 'settings.layoutGrid' },
  { value: 'grouped', icon: Layers, labelKey: 'settings.layoutGrouped' },
]


</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('settings.title') }}</template>

    <div class="space-y-6">
      <div>
        <h3 class="text-sm font-medium text-foreground mb-3">
          {{ t('settings.appearance') }}
        </h3>
        <p class="text-sm text-muted-foreground mb-3">
          {{ t('settings.theme') }}
        </p>
        <div class="flex gap-2">
          <button
            v-for="option in themes"
            :key="option.value"
            class="flex flex-1 flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm transition-colors"
            :class="
              ui.theme === option.value
                ? 'border-primary bg-primary/10 text-foreground'
                : 'border-border bg-card text-muted-foreground hover:bg-accent'
            "
            @click="ui.setTheme(option.value)"
          >
            <component :is="option.icon" class="h-5 w-5" />
            {{ t(option.labelKey) }}
          </button>
        </div>
      </div>

      <div>
        <h3 class="text-sm font-medium text-foreground mb-1">
          {{ t('settings.defaultLayout') }}
        </h3>
        <p class="text-sm text-muted-foreground mb-3">
          {{ t('settings.defaultLayoutHint') }}
        </p>
        <div class="flex gap-2">
          <button
            v-for="option in layouts"
            :key="option.value"
            class="flex flex-1 flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm transition-colors"
            :class="
              ui.bookmarkLayout === option.value
                ? 'border-primary bg-primary/10 text-foreground'
                : 'border-border bg-card text-muted-foreground hover:bg-accent'
            "
            @click="ui.setBookmarkLayout(option.value)"
          >
            <component :is="option.icon" class="h-5 w-5" />
            {{ t(option.labelKey) }}
          </button>
        </div>
      </div>

    </div>
  </DialogCl>


</template>
