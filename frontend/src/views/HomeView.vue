<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { Button } from '@/components/ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const isAddingBookmark = ref(false)
</script>

<template>
  <MainLayout>
    <template #header-actions>
      <Button size="sm" @click="isAddingBookmark = true">
        <Plus class="h-4 w-4 mr-2" />
        {{ t('header.addBookmark') }}
      </Button>
    </template>

    <div class="max-w-4xl mx-auto space-y-6">
      <div class="flex flex-col items-center justify-center py-12 text-center">
        <p class="text-muted-foreground">
          {{ t('home.noBookmarks') }}
        </p>
      </div>
    </div>
  </MainLayout>
</template>
