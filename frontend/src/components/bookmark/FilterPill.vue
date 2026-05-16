<script setup lang="ts">
import type { QueryToken } from '@/lib/searchQuery'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { Calendar, Folder, FolderTree, Hash, Minus, X } from 'lucide-vue-next'
import { type Component, computed } from 'vue'

const props = defineProps<{
  token: QueryToken
}>()

defineEmits<{
  remove: []
}>()

const tagStore = useTagStore()
const folderStore = useFolderStore()

interface PillVariant {
  icon?: Component
  label?: string
  display: string
}

const OPERATOR_VARIANTS: Record<string, { icon: Component; label: string }> = {
  folder: { icon: Folder, label: 'folder:' },
  under: { icon: FolderTree, label: 'under:' },
  created: { icon: Calendar, label: 'created:' },
}

// `under:` tokens carry a folder id (from click paths) or a name (from typed
// queries). Resolve to the folder's name for display; fall back to the raw
// value if the folder isn't found.
function resolveUnderLabel(): string {
  const byId = folderStore.folders.find((f) => f.id === props.token.value)
  return byId ? byId.data.name : props.token.value
}

const pillVariant = computed<PillVariant>(() => {
  if (props.token.kind === 'tag') {
    return { icon: Hash, display: props.token.value }
  }
  if (props.token.kind === 'operator') {
    const known = OPERATOR_VARIANTS[props.token.key]
    if (known) {
      return {
        icon: known.icon,
        label: known.label,
        display: props.token.key === 'under' ? resolveUnderLabel() : props.token.value,
      }
    }
    return { label: `${props.token.key}:`, display: props.token.value }
  }
  return { display: props.token.value }
})

const tagColor = computed(() => {
  if (props.token.kind !== 'tag') return undefined
  const value = props.token.value.toLowerCase()
  const tag = tagStore.tags.find((t) => t.data.name.toLowerCase() === value)
  return tag?.data.color
})

const variantClass = computed(() => {
  if (props.token.neg) {
    return 'bg-destructive/10 text-destructive border-destructive/40 line-through decoration-destructive/60'
  }
  if (props.token.kind === 'tag') {
    return 'bg-[color-mix(in_oklab,var(--tag-color,var(--color-primary))_18%,var(--color-secondary))] border-[var(--tag-color,var(--color-primary))] text-foreground'
  }
  return 'bg-secondary text-foreground border-border'
})
</script>

<template>
  <span
    data-testid="filter-pill"
    :data-token-kind="token.kind"
    :data-token-key="token.kind === 'operator' ? token.key : ''"
    :data-token-value="token.value"
    :data-token-neg="token.neg ? 'true' : 'false'"
    class="inline-flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-full text-xs border transition-colors"
    :class="variantClass"
    :style="tagColor ? { '--tag-color': tagColor } : undefined"
  >
    <Minus v-if="token.neg" class="h-3 w-3 shrink-0" />
    <component
      :is="pillVariant.icon"
      v-if="pillVariant.icon"
      class="h-3 w-3 shrink-0"
      :style="token.kind === 'tag' && !token.neg && tagColor ? { color: 'var(--tag-color)' } : undefined"
    />
    <span v-if="pillVariant.label" class="text-muted-foreground">{{ pillVariant.label }}</span>
    <span>{{ token.kind === 'text' ? `"${pillVariant.display}"` : pillVariant.display }}</span>
    <button
      type="button"
      data-testid="filter-pill-remove"
      class="ml-0.5 h-4 w-4 inline-flex items-center justify-center rounded-full hover:bg-foreground/10 transition-colors"
      aria-label="Remove filter"
      @click="$emit('remove')"
    >
      <X class="h-3 w-3" />
    </button>
  </span>
</template>
