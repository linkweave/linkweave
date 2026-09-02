import {
  type CompiledQuery,
  compileQuery,
  type QueryToken,
  stringifyTokens,
  toggleToken,
  tokenize,
} from '@/lib/searchQuery'
import { registerStoreReset } from '@/lib/storeReset'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * Owns the bookmark search query (raw string + parsed tokens) and the
 * operations on it. Kept separate from the bookmark store so the folder/tag
 * stores can drive sidebar selections through query tokens without a
 * bookmark <-> folder/tag circular dependency.
 */
export const useSearchQueryStore = defineStore('searchQuery', () => {
  const searchQuery = ref('')

  function setSearchQuery(query: string) {
    searchQuery.value = query
  }

  function clearSearchQuery() {
    searchQuery.value = ''
  }

  const queryTokens = computed<QueryToken[]>(() => tokenize(searchQuery.value))

  // Compiled once per query change and shared by every consumer: the bookmark
  // filter matches against it, while the search bar and the empty state read
  // `hasInvalidTokens` off it. One derivation means the red border, the
  // flagged pill and the empty result set can never disagree (UC-070 A2).
  const compiledQuery = computed<CompiledQuery>(() => compileQuery(queryTokens.value))

  const hasInvalidTokens = computed(() => compiledQuery.value.invalid)

  function toggleQueryToken(token: QueryToken, modifier?: 'exclude') {
    const next = toggleToken(queryTokens.value, token, modifier)
    searchQuery.value = stringifyTokens(next)
  }

  function removeQueryTokenAt(idx: number) {
    const next = queryTokens.value.filter((_, i) => i !== idx)
    searchQuery.value = stringifyTokens(next)
  }

  function removeTokensWhere(predicate: (t: QueryToken) => boolean) {
    const remaining = queryTokens.value.filter((t) => !predicate(t))
    searchQuery.value = stringifyTokens(remaining)
  }

  function isTagActive(name: string): boolean {
    const lower = name.toLowerCase()
    return queryTokens.value.some(
      (t) => t.kind === 'tag' && !t.neg && t.value.toLowerCase() === lower,
    )
  }

  function isTagExcluded(name: string): boolean {
    const lower = name.toLowerCase()
    return queryTokens.value.some(
      (t) => t.kind === 'tag' && t.neg && t.value.toLowerCase() === lower,
    )
  }

  // Folder + tag selections are derived from the query, so clearing it on
  // logout resets them too.
  registerStoreReset(clearSearchQuery)

  return {
    searchQuery,
    queryTokens,
    compiledQuery,
    hasInvalidTokens,
    setSearchQuery,
    clearSearchQuery,
    toggleQueryToken,
    removeQueryTokenAt,
    removeTokensWhere,
    isTagActive,
    isTagExcluded,
  }
})
