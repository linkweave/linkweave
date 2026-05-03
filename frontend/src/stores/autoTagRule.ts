import { defineStore } from 'pinia'
import { computed } from 'vue'
import { AutoTagRuleResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { AutoTagRuleJson, AutoTagRuleSaveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'

const ruleApi = new AutoTagRuleResourceApi(config)

export const useAutoTagRuleStore = defineStore('autoTagRule', () => {
  const collectionStore = useCollectionStore()

  const rules = computed<AutoTagRuleJson[]>(
    () => collectionStore.collectionInfo?.autoTagRules ?? [],
  )

  function patch(updater: (list: AutoTagRuleJson[]) => AutoTagRuleJson[]) {
    const info = collectionStore.collectionInfo
    if (info) {
      info.autoTagRules = updater(info.autoTagRules ?? [])
    }
  }

  async function createRule(data: AutoTagRuleSaveJson): Promise<AutoTagRuleJson> {
    const rule = await ruleApi.apiAutoTagRulesPost({ autoTagRuleSaveJson: data })
    patch((list) => [...list, rule])
    return rule
  }

  async function updateRule(ruleId: string, data: AutoTagRuleSaveJson): Promise<AutoTagRuleJson> {
    const updated = await ruleApi.apiAutoTagRulesRuleIdPut({
      ruleId,
      autoTagRuleSaveJson: data,
    })
    patch((list) => {
      const idx = list.findIndex((r) => r.id === ruleId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function deleteRule(ruleId: string): Promise<void> {
    await ruleApi.apiAutoTagRulesRuleIdDelete({ ruleId })
    patch((list) => list.filter((r) => r.id !== ruleId))
  }

  async function reorder(collectionId: string, orderedIds: string[]): Promise<void> {
    await ruleApi.apiAutoTagRulesReorderPut({
      autoTagRuleOrderJson: { collectionId, orderedIds },
    })
    // Re-sort the cached list to match
    patch((list) => {
      const byId = new Map(list.map((r) => [r.id, r]))
      const next: AutoTagRuleJson[] = []
      for (const id of orderedIds) {
        const r = byId.get(id)
        if (r) next.push(r)
      }
      // append any not in the ordered list (shouldn't happen)
      for (const r of list) if (!orderedIds.includes(r.id)) next.push(r)
      return next
    })
  }

  return { rules, createRule, updateRule, deleteRule, reorder }
})
