<script setup lang="ts">
import { ExternalLink } from 'lucide-vue-next'
import type { BookmarkJson } from '@/api/generated'

const props = defineProps<{
  bookmark: BookmarkJson
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
</script>

<template>
  <div class="rounded-lg border border-border bg-card p-4 hover:shadow-sm transition-shadow">
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

        <div v-if="props.bookmark.data.tagIds && props.bookmark.data.tagIds.size > 0" class="flex flex-wrap gap-1 mt-2">
          <span
            v-for="tagId in props.bookmark.data.tagIds"
            :key="tagId"
            class="inline-flex items-center rounded-full bg-secondary text-secondary-foreground px-2 py-0.5 text-xs"
          >
            {{ tagId.substring(0, 8) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
