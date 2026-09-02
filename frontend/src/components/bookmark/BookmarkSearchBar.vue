<script setup lang="ts">
// The bookmark search bar (UC-070): a plain `SearchBar` plus the query
// grammar — tag/folder/property autocomplete, the `url:` paste offer (BR-088)
// and the invalid-syntax border (A2). It is bound to the search-query store
// rather than to a `v-model`, because there is exactly one bookmark query and
// every consumer of this component edits that one.
//
// Split out of `SearchBar` so the grammar cannot leak into search bars that
// filter something else: `CollectionManageView` matches collection names by
// substring, where an operator dropdown would rewrite the field on Enter into
// a query that matches no collection.
import { nextTick, ref } from 'vue'
import { SearchBar } from '@/components/ui'
import SearchAutocompleteDropdown from './SearchAutocompleteDropdown.vue'
import {
  useSearchAutocomplete,
  type AcResult,
  type AcItem,
} from '@/composables/useSearchAutocomplete'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { KNOWN_OPERATORS_HINT } from '@/lib/searchOperators'
import { useI18n } from 'vue-i18n'

withDefaults(
  defineProps<{
    placeholder?: string
    variant?: 'default' | 'header'
  }>(),
  { placeholder: 'Search...', variant: 'default' },
)

const { t } = useI18n()
const searchQueryStore = useSearchQueryStore()
const barRef = ref<InstanceType<typeof SearchBar> | null>(null)

const { parseQueryForAutoCompl } = useSearchAutocomplete()
const acResult = ref<AcResult | null>(null)
const acIdx = ref(0)
const acMouseDown = ref(false)

function refreshAc(value: string, cursor: number) {
  const r = parseQueryForAutoCompl(value, cursor)
  acResult.value = r && r.items.length > 0 ? r : null
  acIdx.value = 0
}

function onBlur() {
  // Selecting a suggestion blurs the input before the click lands. `acMouseDown`
  // (set in onAcMouseDown) tells us the blur was caused by pressing inside the
  // dropdown, so we keep it open until the click runs. We deliberately avoid
  // `@mousedown.prevent` on the dropdown — preventDefault on a synthesized
  // mousedown can swallow the follow-up click on some touch browsers.
  if (!acMouseDown.value) acResult.value = null
}

function onAcKeyDown(e: KeyboardEvent) {
  if (!acResult.value) return
  const n = acResult.value.items.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    acIdx.value = Math.min(acIdx.value + 1, n - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    acIdx.value = Math.max(acIdx.value - 1, 0)
  } else if ((e.key === 'Enter' || e.key === 'Tab') && n > 0) {
    const item = acResult.value.items[acIdx.value]
    if (item) {
      e.preventDefault()
      commit(item)
    }
  } else if (e.key === 'Escape') {
    e.preventDefault()
    acResult.value = null
  }
}

function commit(item: AcItem) {
  if (!acResult.value) return
  const [s, e] = acResult.value.range
  const q = searchQueryStore.searchQuery
  // No trailing space for property:key= — the user types the value next.
  const suffix = item.insert.endsWith('=') ? '' : ' '
  const tail = q.slice(e).replace(/^\s+/, '')
  const newQuery = q.slice(0, s) + item.insert + suffix + tail
  const newCursor = s + item.insert.length + suffix.length

  searchQueryStore.setSearchQuery(newQuery)
  acResult.value = null

  nextTick(() => {
    barRef.value?.focusAt(newCursor)
    // Chain off `newQuery` (the value we just wrote), not the store: the
    // follow-up suggestions must align with the cursor we just set.
    const follow = parseQueryForAutoCompl(newQuery, newCursor)
    if (follow && follow.items.length > 0) {
      acResult.value = follow
      acIdx.value = 0
    }
  })
}

function onAcMouseDown() {
  acMouseDown.value = true
  setTimeout(() => {
    acMouseDown.value = false
  }, 200)
}
</script>

<template>
  <!-- The invalid-syntax signal (UC-070 A2) is only the border here: which
       token is broken, and why, is explained on its pill in the filter strip,
       which has reserved layout space. A floating message under this input
       would clip against the header border and paint over the toolbar. -->
  <SearchBar
    ref="barRef"
    v-model="searchQueryStore.searchQuery"
    :placeholder="placeholder"
    :variant="variant"
    :invalid="searchQueryStore.hasInvalidTokens"
    :invalid-message="t('search.invalidTokenHint', { operators: KNOWN_OPERATORS_HINT })"
    @caret="refreshAc"
    @keydown="onAcKeyDown"
    @blur="onBlur"
  >
    <template #overlay>
      <SearchAutocompleteDropdown
        v-if="acResult"
        :result="acResult"
        :active-idx="acIdx"
        @select="commit"
        @mousedown="onAcMouseDown"
      />
    </template>
  </SearchBar>
</template>
