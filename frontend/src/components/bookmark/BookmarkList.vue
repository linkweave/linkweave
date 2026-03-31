<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useBookmarkStore } from '@/stores/bookmark'
import BookmarkCard from './BookmarkCard.vue'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
</script>

<template>
  <div v-if="bookmarkStore.loading" class="flex items-center justify-center py-12">
    <p class="text-muted-foreground">{{ t('bookmarkList.loading') }}</p>
  </div>

  <div v-else-if="bookmarkStore.bookmarks.length === 0" class="flex flex-col items-center justify-center py-12 text-center">
    <p class="text-muted-foreground">{{ t('bookmarkList.empty') }}</p>
  </div>

  <div v-else class="max-w-4xl mx-auto space-y-3">
    <BookmarkCard
      v-for="bookmark in bookmarkStore.bookmarks"
      :key="bookmark.id"
      :bookmark="bookmark"
    />
  </div>
</template>
