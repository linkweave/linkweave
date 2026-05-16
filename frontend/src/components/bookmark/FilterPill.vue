<script setup lang="ts">
import { computed } from 'vue'
import { Hash, Folder, X, Minus } from 'lucide-vue-next'
import { useTagStore } from '@/stores/tag'
import type { QueryToken } from '@/lib/searchQuery'

const props = defineProps<{
  token: QueryToken
}>()

defineEmits<{
  remove: []
}>()

const tagStore = useTagStore()

const tagColor = computed(() => {
  if (props.token.kind !== 'tag') return undefined
  const value = props.token.value.toLowerCase()
  const tag = tagStore.tags.find(t => t.data.name.toLowerCase() === value)
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
    class="inline-flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-full text-xs border transition-colors"
    :class="variantClass"
    :style="tagColor ? { '--tag-color': tagColor } : undefined"
  >
    <Minus v-if="token.neg" class="h-3 w-3 shrink-0" />
    <template v-if="token.kind === 'tag'">
      <Hash class="h-3 w-3 shrink-0" />
      <span>{{ token.value }}</span>
    </template>
    <template v-else-if="token.kind === 'op' && token.key === 'folder'">
      <Folder class="h-3 w-3 shrink-0" />
      <span class="text-muted-foreground">folder:</span>
      <span>{{ token.value }}</span>
    </template>
    <template v-else-if="token.kind === 'op'">
      <span class="text-muted-foreground">{{ token.key }}:</span>
      <span>{{ token.value }}</span>
    </template>
    <template v-else>
      <span>"{{ token.value }}"</span>
    </template>
    <button
      type="button"
      class="ml-0.5 h-4 w-4 inline-flex items-center justify-center rounded-full hover:bg-foreground/10 transition-colors"
      aria-label="Remove filter"
      @click="$emit('remove')"
    >
      <X class="h-3 w-3" />
    </button>
  </span>
</template>
