<script setup lang="ts">
import type { FolderJson } from '@/api/generated'
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{
  folders: FolderJson[]
  modelValue?: string
  direction?: 'up' | 'down'
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

interface FolderOption {
  id: string
  label: string
  depth: number
  isLast: boolean
}

function buildOptions(parentId: string | null | undefined, depth: number): FolderOption[] {
  const siblings = props.folders.filter((f) => (f.data.parentId ?? null) === (parentId ?? null))
  return siblings.flatMap((f, i) => [
    { id: f.id, label: f.data.name, depth, isLast: i === siblings.length - 1 },
    ...buildOptions(f.id, depth + 1),
  ])
}

const options = computed(() => buildOptions(null, 0))

const emptyLabel = computed(() => props.placeholder ?? 'No folder')

const selectedLabel = computed(() => {
  if (!props.modelValue) return emptyLabel.value
  return options.value.find((o) => o.id === props.modelValue)?.label ?? emptyLabel.value
})

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)

function select(id: string | undefined) {
  emit('update:modelValue', id)
  open.value = false
}

function handleOutsideClick(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', handleOutsideClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', handleOutsideClick))
</script>

<template>
  <div ref="containerRef" class="relative">
    <!-- Trigger -->
    <button
      type="button"
      class="flex h-8 w-full items-center justify-between rounded-md border border-input bg-transparent px-3 py-1 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      @click="open = !open"
    >
      <span :class="!modelValue ? 'text-muted-foreground' : ''">{{ selectedLabel }}</span>
      <svg class="h-3 w-3 text-muted-foreground shrink-0 ml-2 transition-transform" :class="open ? 'rotate-180' : ''" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 4l4 4 4-4"/></svg>
    </button>

    <!-- Dropdown -->
    <div
      v-if="open"
      class="absolute z-50 w-full rounded-md border border-border bg-popover shadow-md overflow-y-auto max-h-48 py-1"
      :class="(direction ?? 'up') === 'up' ? 'bottom-full mb-1' : 'top-full mt-1'"
    >
      <!-- No folder -->
      <div
        class="flex items-center px-3 h-7 text-xs cursor-pointer hover:bg-accent hover:text-accent-foreground"
        :class="!modelValue ? 'text-primary font-medium' : 'text-muted-foreground'"
        @mousedown.prevent="select(undefined)"
      >
        {{ emptyLabel }}
      </div>

      <!-- Folder options -->
      <div
        v-for="opt in options"
        :key="opt.id"
        class="flex items-center h-7 text-xs cursor-pointer hover:bg-accent hover:text-accent-foreground pr-3"
        :class="modelValue === opt.id ? 'text-primary font-medium' : ''"
        :style="{ paddingLeft: `${opt.depth * 16 + 12}px` }"
        @mousedown.prevent="select(opt.id)"
      >
        <!-- Tree connector -->
        <span class="text-muted-foreground mr-1.5 shrink-0" v-if="opt.depth > 0">
          {{ opt.isLast ? '└' : '├' }}
        </span>
        <span class="truncate">{{ opt.label }}</span>
      </div>
    </div>
  </div>
</template>
