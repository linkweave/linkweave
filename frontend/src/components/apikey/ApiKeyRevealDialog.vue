<script setup lang="ts">
import { ButtonCl, DialogCl, DialogFooterCl } from '@/components/ui'
import { Check, Copy, TriangleAlert } from '@lucide/vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  open: boolean
  rawKey: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { t } = useI18n()
const copied = ref(false)

async function copyKey() {
  await navigator.clipboard.writeText(props.rawKey)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('apiKeys.revealTitle') }}</template>

    <div class="space-y-4">
      <div class="flex items-start gap-2 rounded-md bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 px-3 py-2.5 text-sm text-amber-800 dark:text-amber-300">
        <TriangleAlert class="h-4 w-4 shrink-0 mt-0.5" />
        <span>{{ t('apiKeys.revealWarning') }}</span>
      </div>

      <div>
        <p class="text-sm text-muted-foreground mb-1.5">{{ t('apiKeys.revealKeyLabel') }}</p>
        <div class="flex items-center gap-2">
          <code class="flex-1 min-w-0 rounded-md border border-border bg-muted px-3 py-2 text-xs font-mono break-all select-all">
            {{ rawKey }}
          </code>
          <ButtonCl
            type="button"
            variant="outline"
            size="icon"
            :title="t('apiKeys.copy')"
            data-testid="copy-api-key"
            @click="copyKey"
          >
            <Check v-if="copied" class="h-4 w-4 text-green-600" />
            <Copy v-else class="h-4 w-4" />
          </ButtonCl>
        </div>
      </div>
    </div>

    <template #footer>
      <DialogFooterCl>
        <ButtonCl type="button" @click="emit('update:open', false)">
          {{ t('apiKeys.revealDone') }}
        </ButtonCl>
      </DialogFooterCl>
    </template>
  </DialogCl>
</template>
