<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Hash, Folder, Box, Zap } from '@lucide/vue'
import HighlightMatch from './HighlightMatch.vue'
import type { AcResult, AcItem, AcMode } from '@/composables/useSearchAutocomplete'

const props = defineProps<{
  result: AcResult
  activeIdx: number
}>()
const emit = defineEmits<{ select: [item: AcItem] }>()

const { t } = useI18n()
const listEl = ref<HTMLElement | null>(null)

watch(
  () => props.activeIdx,
  async () => {
    await nextTick()
    listEl.value?.querySelector('.ac-sel')?.scrollIntoView({ block: 'nearest' })
  },
)

const modeIcon: Record<AcMode, typeof Hash> = {
  tag: Hash,
  folder: Folder,
  under: Folder,
  'prop-key': Box,
  'prop-val': Box,
  operator: Zap,
}

const headerLabel = computed(() => {
  switch (props.result.mode) {
    case 'tag':
      return t('search.autocomplete.tags')
    case 'folder':
    case 'under':
      return t('search.autocomplete.folders')
    case 'prop-key':
      return t('search.autocomplete.properties')
    case 'operator':
      return t('search.autocomplete.operators')
    case 'prop-val':
      return t('search.autocomplete.propValues', { key: props.result.label })
    default:
      return ''
  }
})

// `hint` is always an i18n key under search.autocomplete (see the composable).
function hintText(item: AcItem): string {
  return item.hint ? t(`search.autocomplete.${item.hint}`) : ''
}
</script>

<template>
  <div
    class="absolute top-[calc(100%+6px)] left-0 right-0 z-50 overflow-hidden rounded-[10px]
           border border-border bg-popover shadow-[0_16px_48px_rgba(0,0,0,.35)]
           animate-in fade-in-0 slide-in-from-top-1 duration-100"
    data-testid="search-autocomplete"
  >
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-border px-2.5 py-1.5">
      <div
        class="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground"
      >
        <component :is="modeIcon[result.mode]" class="h-[11px] w-[11px] opacity-70" />
        {{ headerLabel }}
      </div>
      <div v-if="result.items.length" class="flex gap-1">
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">{{ t('search.autocomplete.hintNav') }}</kbd>
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">{{ t('search.autocomplete.hintConfirm') }}</kbd>
        <kbd class="rounded border border-border bg-muted px-1 py-px text-[9.5px] text-muted-foreground">{{ t('search.autocomplete.hintDismiss') }}</kbd>
      </div>
    </div>

    <!-- List -->
    <div ref="listEl" class="max-h-56 overflow-y-auto">
      <div v-if="!result.items.length" class="px-2.5 py-3 text-center text-xs text-muted-foreground">
        {{ t('search.autocomplete.noMatches') }}
      </div>
      <button
        v-for="(item, idx) in result.items"
        :key="item.key + idx"
        :class="[
          'flex w-full items-center gap-2 px-2.5 py-[5px] text-left transition-colors',
          idx === activeIdx ? 'ac-sel bg-primary/10 text-primary' : 'text-foreground hover:bg-primary/10',
        ]"
        type="button"
        data-testid="ac-item"
        @click="emit('select', item)"
      >
        <!-- Tag color dot -->
        <span
          v-if="item.type === 'tag' && item.color"
          class="h-2 w-2 shrink-0 rounded-sm"
          :style="{ background: item.color }"
        />
        <!-- Mode icon otherwise -->
        <component
          :is="modeIcon[result.mode]"
          v-else
          :class="['h-[11px] w-[11px] shrink-0', idx === activeIdx ? 'text-primary' : 'text-muted-foreground']"
        />

        <!-- Label -->
        <span class="flex-1 font-mono text-[12px]">
          <span :class="idx === activeIdx ? 'text-primary/60' : 'text-muted-foreground'">
            <template v-if="item.type === 'tag'">#</template>
            <template v-else-if="item.type === 'folder'">folder:</template>
            <template v-else-if="item.type === 'under'">under:</template>
            <template v-else-if="item.type === 'prop-key'">property:</template>
            <template v-else-if="item.type === 'prop-val'">property:{{ item.propKey }}=</template>
          </span>
          <HighlightMatch :text="item.label" :filter="item.filter" />
          <span
            v-if="item.type === 'prop-key'"
            :class="idx === activeIdx ? 'text-primary/60' : 'text-muted-foreground'"
            >=</span
          >
        </span>

        <span v-if="item.hint" class="shrink-0 font-sans text-[10.5px] text-muted-foreground">
          {{ hintText(item) }}
        </span>
      </button>
    </div>

    <!-- Footer: syntax reference -->
    <div
      class="flex flex-wrap gap-2 border-t border-border px-2.5 py-1.5 font-mono text-[10.5px] text-muted-foreground"
    >
      <span><b class="font-semibold text-foreground/75">#</b>tag</span>
      <span class="opacity-40">{{ t('search.autocomplete.or') }}</span>
      <span><b class="font-semibold text-foreground/75">tag:</b>name</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">folder:</b>name</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">under:</b>name</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">property:</b>k=v</span>
      <span class="opacity-30">·</span>
      <span><b class="font-semibold text-foreground/75">-</b>{{ t('search.autocomplete.negate') }}</span>
    </div>
  </div>
</template>
