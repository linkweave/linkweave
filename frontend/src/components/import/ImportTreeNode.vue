<script setup lang="ts">
import type { ImportNodeJson } from '@/api/generated'
import { ImportNodeType } from '@/api/generated'
import ImportCheckbox from '@/components/import/ImportCheckbox.vue'
import type { useImportSelection } from '@/composables/useImportSelection'
import { ChevronRight, Folder as FolderIcon } from '@lucide/vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    node: ImportNodeJson
    model: ReturnType<typeof useImportSelection>
    depth?: number
  }>(),
  { depth: 0 },
)

const { t } = useI18n()
const expanded = ref(true)

const isFolder = computed(() => props.node.type === ImportNodeType.Folder)
const folderState = computed(() => props.model.folderState(props.node))

const totalUnder = computed(() => props.model.bookmarkIdsUnder(props.node).length)
const selectedUnder = computed(
  () => props.model.bookmarkIdsUnder(props.node).filter((id) => props.model.isSelected(id)).length,
)
const isDuplicate = computed(() => props.node.url != null && props.model.isDuplicate(props.node.id))
const selected = computed(() => props.model.isSelected(props.node.id))

// Indent each level; the row stays full-width so the hover target is the row.
const indent = computed(() => `${props.depth * 1.25}rem`)

const folderAriaLabel = computed(() =>
  t('importReview.folderSelected', {
    name: props.node.name,
    selected: selectedUnder.value,
    total: totalUnder.value,
  }),
)
</script>

<template>
  <div>
    <!-- Folder row -->
    <div
      v-if="isFolder"
      class="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-accent"
      :style="{ paddingLeft: indent }"
      :data-testid="`import-folder-${node.name}`"
    >
      <button
        type="button"
        class="grid h-5 w-5 shrink-0 place-items-center text-muted-foreground"
        :aria-expanded="expanded"
        :aria-label="expanded ? t('common.collapse') : t('common.expand')"
        @click="expanded = !expanded"
      >
        <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': expanded }" />
      </button>
      <ImportCheckbox
        :state="folderState"
        :label="folderAriaLabel"
        @toggle="model.toggleFolder(node)"
      />
      <FolderIcon class="h-4 w-4 shrink-0 text-muted-foreground" />
      <span class="truncate text-sm font-medium">{{ node.name }}</span>
      <span class="ml-auto shrink-0 text-xs tabular-nums text-muted-foreground">
        {{ selectedUnder }}/{{ totalUnder }}
      </span>
    </div>

    <!-- Bookmark row -->
    <div
      v-else
      class="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-accent"
      :class="{ 'opacity-50': isDuplicate && !selected }"
      :style="{ paddingLeft: `calc(${indent} + 1.75rem)` }"
      :data-testid="`import-bookmark-${node.name}`"
      :data-selected="selected"
      :data-duplicate="isDuplicate"
    >
      <ImportCheckbox
        :state="selected ? 'all' : 'none'"
        :label="node.name"
        dense
        @toggle="model.toggleBookmark(node.id)"
      />
      <span class="truncate text-sm">{{ node.name }}</span>
      <span v-if="node.url" class="truncate text-xs text-muted-foreground">{{ node.url }}</span>
      <span
        v-if="isDuplicate"
        class="ml-auto shrink-0 rounded px-1.5 py-0.5 text-[11px] font-medium text-duplicate"
        style="background-color: color-mix(in oklch, var(--color-duplicate) 12%, transparent)"
      >
        {{ t('importReview.inLibrary') }}
      </span>
    </div>

    <!-- Children -->
    <div v-if="isFolder && expanded && node.children?.length">
      <ImportTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :model="model"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>
