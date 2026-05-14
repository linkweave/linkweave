<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from 'radix-vue'
import { ArrowDown, ArrowUp, Check, ChevronDown, Info, RotateCcw } from 'lucide-vue-next'
import { SortDirection, SortField } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'

const { t } = useI18n()
const collectionStore = useCollectionStore()

type FieldOption = {
  id: SortField
  label: string
  ascLabel: string
  descLabel: string
}

const FIELDS: ReadonlyArray<FieldOption> = [
  { id: SortField.Title,       label: 'sort.field.title',       ascLabel: 'sort.dir.az',          descLabel: 'sort.dir.za' },
  { id: SortField.DateAdded,   label: 'sort.field.dateAdded',   ascLabel: 'sort.dir.oldestFirst', descLabel: 'sort.dir.newestFirst' },
  { id: SortField.LastClicked, label: 'sort.field.lastClicked', ascLabel: 'sort.dir.oldestFirst', descLabel: 'sort.dir.mostRecent' },
  { id: SortField.ClickCount,  label: 'sort.field.clickCount',  ascLabel: 'sort.dir.leastVisited', descLabel: 'sort.dir.mostVisited' },
]

const currentField = computed(() => collectionStore.sortField)
const currentDir = computed(() => collectionStore.sortDirection)
const currentFieldLabel = computed(() => {
  const f = FIELDS.find((o) => o.id === currentField.value)
  return f ? t(f.label) : ''
})
const showSharedClicksNote = computed(
  () => currentField.value === SortField.LastClicked || currentField.value === SortField.ClickCount,
)

function pick(field: SortField) {
  const id = collectionStore.currentCollectionId
  if (!id) return
  // Preserve direction when changing field.
  collectionStore.updateSettings(id, { sortField: field, sortDirection: currentDir.value })
}

function flipDir(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  const id = collectionStore.currentCollectionId
  if (!id) return
  const next = currentDir.value === SortDirection.Asc ? SortDirection.Desc : SortDirection.Asc
  collectionStore.updateSettings(id, { sortField: currentField.value, sortDirection: next })
}

function reset() {
  const id = collectionStore.currentCollectionId
  if (!id) return
  void collectionStore.resetSortPreference(id)
}
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button
        type="button"
        data-testid="bookmark-sort-trigger"
        class="inline-flex h-7 items-center gap-1.5 rounded-md border border-border bg-card px-2 text-xs text-foreground transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      >
        <span class="text-muted-foreground">{{ t('sort.label') }}:</span>
        <span class="font-medium">{{ currentFieldLabel }}</span>
        <component
          :is="currentDir === SortDirection.Desc ? ArrowDown : ArrowUp"
          class="h-3 w-3 text-muted-foreground"
        />
        <ChevronDown class="h-3 w-3 text-muted-foreground" />
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        align="end"
        :side-offset="4"
        class="z-[100] w-72 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-xl ring-1 ring-black/5 dark:ring-white/10 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
      >
        <DropdownMenuLabel class="flex items-center justify-between px-2 py-1.5 text-xs">
          <span class="font-medium">{{ t('sort.menu.title') }}</span>
          <button
            v-if="collectionStore.hasSortOverride"
            type="button"
            data-testid="bookmark-sort-reset"
            class="inline-flex items-center gap-1 text-muted-foreground hover:text-foreground"
            @click="reset"
          >
            <RotateCcw class="h-3 w-3" />
            {{ t('sort.menu.reset') }}
          </button>
        </DropdownMenuLabel>
        <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />

        <DropdownMenuItem
          v-for="f in FIELDS"
          :key="f.id"
          :data-testid="`bookmark-sort-option-${f.id}`"
          :data-active="f.id === currentField ? 'true' : 'false'"
          class="flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground data-[active=true]:bg-accent/60"
          @select="(e: Event) => { e.preventDefault(); pick(f.id) }"
        >
          <Check class="h-3.5 w-3.5" :class="f.id === currentField ? '' : 'invisible'" />
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">{{ t(f.label) }}</div>
            <div class="text-xs text-muted-foreground">
              {{ f.id === currentField
                  ? t(currentDir === SortDirection.Asc ? f.ascLabel : f.descLabel)
                  : t(`sort.field.${f.id.toLowerCase()}Sub`) }}
            </div>
          </div>
          <div
            v-if="f.id === currentField"
            class="flex overflow-hidden rounded border border-border"
            @click.stop
          >
            <button
              type="button"
              class="px-1.5 py-0.5"
              :class="currentDir === SortDirection.Asc ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground'"
              :aria-pressed="currentDir === SortDirection.Asc"
              :aria-label="t(f.ascLabel)"
              @click="flipDir"
              @pointerdown.stop
            >
              <ArrowUp class="h-3 w-3" />
            </button>
            <button
              type="button"
              class="px-1.5 py-0.5"
              :class="currentDir === SortDirection.Desc ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground'"
              :aria-pressed="currentDir === SortDirection.Desc"
              :aria-label="t(f.descLabel)"
              @click="flipDir"
              @pointerdown.stop
            >
              <ArrowDown class="h-3 w-3" />
            </button>
          </div>
        </DropdownMenuItem>

        <template v-if="showSharedClicksNote">
          <DropdownMenuSeparator class="-mx-1 my-1 h-px bg-border" />
          <div class="flex items-start gap-2 px-2 py-1.5 text-xs text-muted-foreground">
            <Info class="h-3 w-3 mt-0.5 shrink-0" />
            <span>{{ t('sort.menu.sharedClicksNote') }}</span>
          </div>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
