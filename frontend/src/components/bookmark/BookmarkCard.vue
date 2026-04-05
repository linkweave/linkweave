<script setup lang="ts">
import { ExternalLink, MoreHorizontal } from 'lucide-vue-next'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'radix-vue'
import type { BookmarkJson } from '@/api/generated'
import { useTagStore } from '@/stores/tag'
import { useFolderStore } from '@/stores/folder'

const tagStore = useTagStore()
const folderStore = useFolderStore()

const props = defineProps<{
  bookmark: BookmarkJson
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

function extractHostname(url: string) {
  try {
    return new URL(url).hostname
  } catch {
    return ''
  }
}

function faviconUrl(url: string) {
  const hostname = extractHostname(url)
  return hostname
    ? `https://icons.duckduckgo.com/ip3/${hostname}.ico`
    : ''
}

function getTagById(tagId: string) {
  return tagStore.tags.find(t => t.id === tagId)
}

function getFolderName(): string | null {
  const folderId = props.bookmark.data.folderId
  if (!folderId) return null
  const folder = folderStore.folders.find(f => f.id === folderId)
  return folder?.data.name ?? null
}
</script>

<template>
  <div :data-testid="`bookmark-card-${props.bookmark.data.title}`" class="group rounded-lg border border-border bg-card p-4 hover:ring-2 hover:ring-primary/50 hover:border-primary/30 transition-all text-muted-foreground hover:text-accent-foreground">
    <DropdownMenuRoot>
      <div class="flex items-start gap-3">
        <img
          v-if="faviconUrl(props.bookmark.data.url)"
          :src="faviconUrl(props.bookmark.data.url)"
          :alt="''"
          class="w-5 h-5 mt-0.5 rounded-sm shrink-0"
          loading="lazy"
          @error="($event.target as HTMLImageElement).style.display = 'none'"
        />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <h3 class="font-medium text-foreground truncate">
              {{ props.bookmark.data.title }}
            </h3>
            <DropdownMenuTrigger as-child>
              <button
                class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md opacity-0 group-hover:opacity-100 transition-opacity hover:bg-primary hover:text-primary-foreground"
                @click.stop
              >
                <MoreHorizontal class="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
          </div>

          <a
            :href="props.bookmark.data.url"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center gap-1 text-sm text-muted-foreground hover:text-primary transition-colors mt-0.5"
          >
            <span class="truncate">{{ props.bookmark.data.url }}</span>
            <ExternalLink class="h-3 w-3 shrink-0" />
          </a>

          <p
            v-if="props.bookmark.data.description"
            class="text-sm text-muted-foreground mt-2 line-clamp-2"
          >
            {{ props.bookmark.data.description }}
          </p>

          <div v-if="props.bookmark.data.tagIds && props.bookmark.data.tagIds.size > 0 || getFolderName()" class="flex flex-wrap items-center gap-1 mt-2">
            <span v-if="getFolderName()" class="text-xs text-muted-foreground">
              in {{ getFolderName() }}
            </span>
            <span
              v-for="tagId in props.bookmark.data.tagIds"
              :key="tagId"
              class="inline-flex items-center rounded-full px-2 py-0.5 text-xs text-white"
              :style="{ backgroundColor: getTagById(tagId)?.data.color ?? '#64748b' }"
            >
              {{ getTagById(tagId)?.data.name ?? tagId.substring(0, 8) }}
            </span>
          </div>
        </div>
      </div>
      <DropdownMenuPortal>
        <DropdownMenuContent
          class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuItem
            data-testid="bookmark-edit-btn"
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('edit', props.bookmark)"
          >
            {{ $t('common.edit') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            data-testid="bookmark-move-btn"
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('move', props.bookmark)"
          >
            {{ $t('bookmark.moveToFolder') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            data-testid="bookmark-delete-btn"
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors text-destructive focus:text-destructive data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('delete', props.bookmark)"
          >
            {{ $t('common.delete') }}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenuPortal>
    </DropdownMenuRoot>
  </div>
</template>
