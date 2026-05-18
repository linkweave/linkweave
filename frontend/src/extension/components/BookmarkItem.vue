<script setup lang="ts">
import type { BookmarkJson, TagJson } from '@/api/generated'
import { ExternalLink } from '@lucide/vue'

defineProps<{
  bookmark: BookmarkJson
  hostname: string
  tags: TagJson[]
  folderName?: string
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <a
    :href="bookmark.data.url"
    target="_blank"
    rel="noopener noreferrer"
    class="flex items-start gap-2.5 rounded-md px-2 py-2 hover:bg-accent transition-colors group"
    @click="emit('click')"
  >
    <!-- Favicon -->
    <img
      v-if="hostname"
      :src="`https://icons.duckduckgo.com/ip3/${hostname}.ico`"
      :alt="''"
      class="w-4 h-4 mt-0.5 rounded-sm shrink-0"
      loading="lazy"
      @error="($event.target as HTMLImageElement).style.display = 'none'"
    />
    <div v-else class="w-4 h-4 mt-0.5 shrink-0" />

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-1">
        <span class="text-xs font-medium text-foreground truncate">{{ bookmark.data.title }}</span>
        <ExternalLink class="h-3 w-3 text-muted-foreground shrink-0 opacity-0 group-hover:opacity-100 transition-opacity" />
      </div>
      <span class="text-[10px] text-muted-foreground truncate block">{{ bookmark.data.url }}</span>

      <!-- Tags + folder -->
      <div
        v-if="tags.length > 0 || folderName"
        class="flex flex-wrap items-center gap-1 mt-1"
      >
        <span v-if="folderName" class="text-[10px] text-muted-foreground">{{ folderName }}</span>
        <span
          v-for="tag in tags"
          :key="tag.id"
          class="inline-flex items-center rounded-full px-1.5 py-0 text-[10px] text-white"
          :style="{ backgroundColor: tag.data.color ?? '#64748b' }"
        >
          {{ tag.data.name }}
        </span>
      </div>
    </div>
  </a>
</template>
