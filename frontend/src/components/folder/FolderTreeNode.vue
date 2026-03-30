<script setup lang="ts">
import { Folder } from 'lucide-vue-next'
import {
  ContextMenuRoot,
  ContextMenuTrigger,
  ContextMenuPortal,
  ContextMenuContent,
  ContextMenuItem,
} from 'radix-vue'
import { useI18n } from 'vue-i18n'
import type { FolderJson } from '@/api/generated'

const { t } = useI18n()

interface FolderNode {
  folder: FolderJson
  children: FolderNode[]
}

interface Props {
  nodes: FolderNode[]
  depth?: number
}

withDefaults(defineProps<Props>(), { depth: 0 })

const emit = defineEmits<{
  createSubfolder: [parentId: string]
}>()
</script>

<template>
  <ul class="space-y-0.5">
    <li v-for="node in nodes" :key="node.folder.id">
      <ContextMenuRoot>
        <ContextMenuTrigger as-child>
          <div
            class="flex items-center gap-2 rounded-md py-1.5 text-sm cursor-pointer transition-colors hover:bg-accent hover:text-accent-foreground"
            :style="{ paddingLeft: `${depth * 16 + 8}px`, paddingRight: '8px' }"
          >
            <Folder class="h-4 w-4 shrink-0 text-muted-foreground" />
            <span class="truncate">{{ node.folder.data.name }}</span>
          </div>
        </ContextMenuTrigger>
        <ContextMenuPortal>
          <ContextMenuContent
            class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
          >
            <ContextMenuItem
              class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              @select="emit('createSubfolder', node.folder.id)"
            >
              {{ t('folder.createSubfolder') }}
            </ContextMenuItem>
          </ContextMenuContent>
        </ContextMenuPortal>
      </ContextMenuRoot>

      <FolderTreeNode
        v-if="node.children.length > 0"
        :nodes="node.children"
        :depth="depth + 1"
        @create-subfolder="emit('createSubfolder', $event)"
      />
    </li>
  </ul>
</template>
