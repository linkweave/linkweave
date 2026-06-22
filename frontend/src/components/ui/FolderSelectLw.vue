<script setup lang="ts">
import { Folder } from '@lucide/vue'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent } from 'radix-vue'
import type { FolderJson } from '@/api/generated'
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    folders: FolderJson[]
    modelValue?: string
    placeholder?: string
    excludeIds?: Set<string>
    id?: string
    direction?: 'up' | 'down'
  }>(),
  {
    direction: 'up',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

interface FolderOption {
  id: string
  label: string
  depth: number
  guides: boolean[]
  isLast: boolean
  color?: string
}

function buildOptions(parentId: string | null | undefined, depth: number, guides: boolean[]): FolderOption[] {
  const siblings = props.folders
    .filter((f) => (f.data.parentId ?? null) === (parentId ?? null))
    .filter((f) => !props.excludeIds?.has(f.id))
  return siblings.flatMap((f, i) => {
    const isLast = i === siblings.length - 1
    return [
      { id: f.id, label: f.data.name, depth, guides: [...guides], isLast, color: f.data.color },
      ...buildOptions(f.id, depth + 1, [...guides, !isLast]),
    ]
  })
}

const options = computed(() => buildOptions(null, 0, []))

const emptyLabel = computed(() => props.placeholder ?? 'No folder')

const selectedLabel = computed(() => {
  if (!props.modelValue) return emptyLabel.value
  return options.value.find((o) => o.id === props.modelValue)?.label ?? emptyLabel.value
})

const open = ref(false)

function select(id: string | undefined) {
  emit('update:modelValue', id)
  open.value = false
}

const side = computed(() => (props.direction === 'up' ? 'top' : 'bottom'))
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger
      :id="id"
      type="button"
      aria-haspopup="listbox"
      :aria-expanded="open"
      class="flex h-9 w-full items-center justify-between rounded-md border border-input bg-input px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
    >
      <span :class="!modelValue ? 'text-muted-foreground' : ''">{{ selectedLabel }}</span>
      <svg
        aria-hidden="true"
        class="h-3.5 w-3.5 text-muted-foreground shrink-0 ml-2 transition-transform"
        :class="open ? 'rotate-180' : ''"
        viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5"
      ><path d="M2 4l4 4 4-4"/></svg>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        :side="side"
        align="start"
        :side-offset="4"
        :collision-padding="8"
        role="listbox"
        class="z-[300] rounded-md border border-border bg-popover shadow-xl ring-1 ring-black/5 dark:ring-white/10 overflow-y-auto py-1"
        :style="{
          width: 'var(--radix-popover-trigger-width)',
          maxHeight: 'min(14rem, var(--radix-popover-content-available-height))',
        }"
      >
        <!-- Empty option -->
        <div
          role="option"
          :aria-selected="!modelValue"
          class="flex items-center px-3 h-7 pointer-coarse:h-10 text-sm cursor-pointer hover:bg-popover-hover hover:text-popover-foreground"
          :class="!modelValue ? 'text-primary font-medium' : 'text-muted-foreground'"
          @mousedown.prevent="select(undefined)"
        >
          {{ emptyLabel }}
        </div>

        <!-- Folder options -->
        <div
          v-for="opt in options"
          :key="opt.id"
          role="option"
          :aria-selected="modelValue === opt.id"
          class="flex items-center h-7 pointer-coarse:h-10 text-sm cursor-pointer hover:bg-popover-hover hover:text-popover-foreground pr-3"
          :class="modelValue === opt.id ? 'text-primary font-medium' : ''"
          @mousedown.prevent="select(opt.id)"
        >
          <template v-if="opt.depth > 0">
            <!-- Ancestor continuation lines -->
            <span
              v-for="(hasLine, gi) in opt.guides"
              :key="gi"
              class="relative w-4 shrink-0 self-stretch"
            >
              <span v-if="hasLine" class="absolute left-1/2 top-0 bottom-0 border-l border-solid border-border"></span>
            </span>
            <!-- Branch connector: ├ or └ -->
            <span class="relative w-4 shrink-0 mr-1 self-stretch">
              <span class="absolute left-1/2 top-0 border-l border-solid border-border" :class="opt.isLast ? 'h-1/2' : 'h-full'"></span>
              <span class="absolute left-1/2 top-1/2 w-1/2 border-t border-solid border-border"></span>
            </span>
          </template>
          <span v-else class="w-3 shrink-0"></span>
          <Folder
            class="h-4 w-4 shrink-0 mr-1"
            :class="opt.color ? '' : 'text-primary'"
            :style="opt.color ? { color: opt.color } : undefined"
          />
          <span class="truncate flex items-center">{{ opt.label }}</span>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
