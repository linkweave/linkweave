<script setup lang="ts">
import type { TagJson } from '@/api/generated'
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { X, ChevronDown } from 'lucide-vue-next'

const props = defineProps<{
  tags: TagJson[]
  selected: Set<string>
  placeholder?: string
  direction?: 'up' | 'down'
}>()

const emit = defineEmits<{
  toggle: [tagId: string]
  clear: []
}>()

const query = ref('')
const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)

const selectedTags = computed(() => props.tags.filter((t) => props.selected.has(t.id)))

const filteredTags = computed(() => {
  const q = query.value.toLowerCase()
  return props.tags.filter((t) => t.data.name.toLowerCase().includes(q))
})

function toggle(tagId: string) {
  emit('toggle', tagId)
  query.value = ''
}

function toggleOpen() {
  open.value = !open.value
  if (open.value) inputRef.value?.focus()
  else query.value = ''
}

function onInputFocus() {
  open.value = true
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    open.value = false
    query.value = ''
    inputRef.value?.blur()
  }
}

function handleOutsideClick(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
    query.value = ''
  }
}

onMounted(() => document.addEventListener('mousedown', handleOutsideClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', handleOutsideClick))
</script>

<template>
  <div ref="containerRef" class="relative">
    <!-- Input area with selected pills -->
    <div
      class="flex flex-wrap gap-1 min-h-8 w-full rounded-md border border-input bg-transparent px-2 py-1 text-xs shadow-sm focus-within:ring-1 focus-within:ring-ring"
    >
      <!-- Selected tag pills -->
      <span
        v-for="tag in selectedTags"
        :key="tag.id"
        class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] text-white shrink-0"
        :style="{ backgroundColor: tag.data.color ?? '#64748b' }"
      >
        {{ tag.data.name }}
        <button
          type="button"
          class="hover:opacity-70"
          @click.stop="emit('toggle', tag.id)"
        >
          <X class="h-2.5 w-2.5" />
        </button>
      </span>

      <!-- Search input -->
      <input
        ref="inputRef"
        v-model="query"
        type="text"
        class="flex-1 min-w-16 bg-transparent outline-none placeholder:text-muted-foreground cursor-text"
        :placeholder="selected.size === 0 ? (placeholder ?? 'Filter by tag…') : ''"
        @focus="onInputFocus"
        @keydown="onKeydown"
      />

      <!-- Right-side buttons -->
      <div class="ml-auto flex items-center gap-1 shrink-0">
        <button
          v-if="selected.size > 0"
          type="button"
          class="text-muted-foreground hover:text-foreground"
          @click.stop="emit('clear')"
        >
          <X class="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          class="text-muted-foreground hover:text-foreground"
          @click.stop="toggleOpen"
        >
          <ChevronDown class="h-3.5 w-3.5 transition-transform" :class="open ? 'rotate-180' : ''" />
        </button>
      </div>
    </div>

    <!-- Dropdown -->
    <div
      v-if="open && filteredTags.length > 0"
      class="absolute z-50 w-full rounded-md border border-border bg-popover shadow-md overflow-y-auto max-h-40 py-1"
      :class="(direction ?? 'up') === 'up' ? 'bottom-full mb-1' : 'top-full mt-1'"
    >
      <div
        v-for="tag in filteredTags"
        :key="tag.id"
        class="flex items-center gap-2 px-3 h-7 cursor-pointer hover:bg-accent hover:text-accent-foreground"
        @mousedown.prevent="toggle(tag.id)"
      >
        <span
          class="w-2.5 h-2.5 rounded-full shrink-0"
          :style="{ backgroundColor: tag.data.color ?? '#64748b' }"
        />
        <span class="flex-1 text-xs truncate">{{ tag.data.name }}</span>
        <span v-if="selected.has(tag.id)" class="text-primary text-xs">✓</span>
      </div>
    </div>
  </div>
</template>
