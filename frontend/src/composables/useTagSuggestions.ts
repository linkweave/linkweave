import { computed, ref, watch, type Ref } from 'vue'
import type { TagJson, TagSaveJson } from '@/api/generated'
import {
  compileCustomRules,
  suggestAllTagNames,
  type CompiledCustomRule,
  type CustomRuleInput,
} from '@/lib/tag-suggester'

export interface TagSuggestion {
  name: string
  existingTagId: string | null
}

export type CreateTagFn = (data: TagSaveJson) => Promise<TagJson>

export interface UseTagSuggestionsOptions {
  url: Ref<string | undefined>
  tags: Ref<TagJson[]>
  collectionId: Ref<string>
  createTag: CreateTagFn
  /** Optional user-defined rules; mixed in with built-ins. */
  customRules?: Ref<CustomRuleInput[]>
}

export function useTagSuggestions(opts: UseTagSuggestionsOptions) {
  const { url, tags, collectionId, createTag, customRules } = opts
  const accepted = ref(false)
  const selectedNames = ref<Set<string>>(new Set())

  const compiledCustom = computed<CompiledCustomRule[]>(() =>
    compileCustomRules(customRules?.value ?? []),
  )

  const allSuggestions = computed<TagSuggestion[]>(() => {
    const names = suggestAllTagNames(url.value ?? '', compiledCustom.value)
    return names.map((name) => {
      const lower = name.toLowerCase()
      const existing = tags.value.find((t) => t.data.name.toLowerCase() === lower)
      return { name, existingTagId: existing?.id ?? null }
    })
  })

  const suggestions = computed<TagSuggestion[]>(() =>
    accepted.value ? [] : allSuggestions.value,
  )

  watch(
    () => url.value,
    () => {
      accepted.value = false
      selectedNames.value = new Set(allSuggestions.value.map((s) => s.name))
    },
    { immediate: true },
  )

  function toggle(name: string) {
    const next = new Set(selectedNames.value)
    if (next.has(name)) next.delete(name)
    else next.add(name)
    selectedNames.value = next
  }

  async function acceptInto<T extends Set<string> | undefined>(
    currentTagIds: Ref<T>,
  ) {
    const next = new Set<string>(currentTagIds.value ?? [])
    const errors: string[] = []
    for (const suggestion of suggestions.value) {
      if (!selectedNames.value.has(suggestion.name)) continue
      try {
        let id = suggestion.existingTagId
        if (!id) {
          const created = await createTag({
            collectionId: collectionId.value,
            name: suggestion.name,
          })
          id = created.id
        }
        next.add(id)
      } catch {
        errors.push(suggestion.name)
      }
    }
    currentTagIds.value = next as T
    selectedNames.value = new Set()
    accepted.value = true
    if (errors.length > 0) {
      throw new Error(
        `Failed to create tag(s): ${errors.join(', ')}`,
      )
    }
  }

  return { suggestions, selectedNames, toggle, acceptInto }
}
