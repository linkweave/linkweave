<script setup lang="ts">
import { requireValue } from '@/lib/nullish.ts'
import { Folder, FolderOpen, ChevronRight, MoreHorizontal } from 'lucide-vue-next'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'radix-vue'
import type { FolderJson } from '@/api/generated'
import { reactive } from 'vue'
import { useFolderStore } from '@/stores/folder'

const folderStore = useFolderStore()

withDefaults(defineProps<{
  nodes: FolderNode[]
  depth?: number
}>(), { depth: 0 })

const emit = defineEmits<{
  createSubfolder: [parentId: string]
  rename: [folder: FolderJson]
  delete: [folder: FolderJson]
}>()

interface FolderNode {
  folder: FolderJson
  children: FolderNode[]
}

const expanded = reactive<Record<string, boolean>>({})

function isExpanded(folderId: string): boolean {
  return expanded[folderId] ?? true
}

function toggleExpand(folderId: string) {
  expanded[folderId] = !isExpanded(folderId)
}
</script>

<template>
  <ul class="space-y-0.5">
    <li v-for="node in nodes" :key="node.folder.id">
      <DropdownMenuRoot>
        <div
          class="group flex items-center gap-1 rounded-md py-1.5 pr-2 text-sm cursor-pointer transition-colors"
          :class="folderStore.selectedFolderId === node.folder.id
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'"
          :style="{ paddingLeft: `${requireValue(depth) * 16 + 8}px` }"
          @click="folderStore.selectFolder(node.folder.id)"
        >
          <button
            class="p-0.5 rounded transition-transform"
            :class="{ 'invisible': node.children.length === 0 }"
            @click.stop="toggleExpand(node.folder.id)"
          >
            <ChevronRight
              class="h-3.5 w-3.5 transition-transform"
              :class="{ 'rotate-90': isExpanded(node.folder.id) && node.children.length > 0 }"
            />
          </button>
          <component
            :is="isExpanded(node.folder.id) && node.children.length > 0 ? FolderOpen : Folder"
            class="h-4 w-4 shrink-0 text-primary"
          />
          <span class="flex-1 truncate">{{ node.folder.data.name }}</span>
          <DropdownMenuTrigger as-child>
            <button
              class="ml-auto h-6 w-6 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground"
              @click.stop
            >
              <MoreHorizontal class="h-3.5 w-3.5" />
            </button>
          </DropdownMenuTrigger>
        </div>
        <DropdownMenuPortal>
          <DropdownMenuContent
            class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
            align="end"
            :side-offset="4"
          >
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('createSubfolder', node.folder.id)"
            >
              {{ $t('folder.createSubfolder') }}
            </DropdownMenuItem>
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('rename', node.folder)"
            >
              {{ $t('common.edit') }}
            </DropdownMenuItem>
            <DropdownMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors text-destructive focus:text-destructive data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('delete', node.folder)"
            >
              {{ $t('common.delete') }}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>

      <FolderTreeNode
        v-if="node.children.length > 0 && isExpanded(node.folder.id)"
        :nodes="node.children"
        :depth="requireValue(depth) + 1"
        @create-subfolder="emit('createSubfolder', $event)"
        @rename="emit('rename', $event)"
        @delete="emit('delete', $event)"
      />
    </li>
  </ul>
</template>
