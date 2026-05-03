<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ButtonCl, DialogCl, HelpPopoverCl } from '@/components/ui'
import { useAutoTagRuleStore } from '@/stores/autoTagRule'
import { useNotificationStore } from '@/stores/notification'
import AutoTagRuleEditDialog from './AutoTagRuleEditDialog.vue'
import type { AutoTagRuleJson } from '@/api/generated'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'

const { t } = useI18n()
const ruleStore = useAutoTagRuleStore()
const notification = useNotificationStore()

interface Props {
  collectionId: string
  open?: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const editorOpen = ref(false)
const editingRule = ref<AutoTagRuleJson | null>(null)

function openCreate() {
  editingRule.value = null
  editorOpen.value = true
}

function openEdit(rule: AutoTagRuleJson) {
  editingRule.value = rule
  editorOpen.value = true
}

async function deleteRule(rule: AutoTagRuleJson) {
  try {
    await ruleStore.deleteRule(rule.id)
  } catch (err) {
    notification.handleApiError(err, t('autoTagRule.deleteError'))
  }
}

async function toggleEnabled(rule: AutoTagRuleJson) {
  try {
    await ruleStore.updateRule(rule.id, {
      ...rule.data,
      enabled: !rule.data.enabled,
    })
  } catch (err) {
    notification.handleApiError(err, t('autoTagRule.saveError'))
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>
      <span class="flex items-center gap-2">
        {{ t('autoTagRule.managerTitle') }}
        <HelpPopoverCl>
          <p class="font-medium mb-1">{{ t('autoTagRule.helpHeader') }}</p>
          <p class="text-muted-foreground">{{ t('autoTagRule.managerHelp') }}</p>
        </HelpPopoverCl>
      </span>
    </template>

    <div class="space-y-3">
      <div class="flex justify-end">
        <ButtonCl
          type="button"
          variant="outline"
          size="sm"
          data-testid="auto-tag-rules-add"
          @click="openCreate"
        >
          <Plus class="h-4 w-4 mr-1" />
          {{ t('autoTagRule.add') }}
        </ButtonCl>
      </div>

      <p
        v-if="ruleStore.rules.length === 0"
        class="text-sm text-muted-foreground py-4 text-center"
      >
        {{ t('autoTagRule.empty') }}
      </p>

      <ul v-else class="space-y-2">
        <li
          v-for="rule in ruleStore.rules"
          :key="rule.id"
          :data-testid="`auto-tag-rule-${rule.id}`"
          class="flex items-center gap-2 rounded-md border border-border p-2"
          :class="{ 'opacity-60': !rule.data.enabled }"
        >
<!--          enable rule -->
          <input
            type="checkbox"
            :checked="rule.data.enabled"
            class="h-4 w-4"
            :title="t('autoTagRule.enabled')"
            @change="toggleEnabled(rule)"
          />
          <div class="flex-1 min-w-0">
            <code class="block font-mono text-xs truncate">{{ rule.data.pattern }}</code>
            <div class="mt-0.5 flex flex-wrap gap-1">
              <span
                v-for="name in rule.data.tagNames.split(',')"
                :key="name"
                class="inline-flex items-center rounded-full border border-dashed border-input px-1.5 py-0.5 text-[10px]"
              >
                {{ name.trim() }}
              </span>
            </div>
            <p
              v-if="rule.data.description"
              class="mt-0.5 text-[11px] text-muted-foreground truncate"
            >
              {{ rule.data.description }}
            </p>
          </div>
<!--          edit rule -->
          <button
            type="button"
            class="text-muted-foreground hover:text-foreground"
            :title="t('common.edit')"
            @click="openEdit(rule)"
          >
            <Pencil class="h-4 w-4" />
          </button>
<!--          delete rule-->
          <button
            type="button"
            class="text-muted-foreground hover:text-destructive"
            :title="t('common.delete')"
            @click="deleteRule(rule)"
          >
            <Trash2 class="h-4 w-4" />
          </button>
        </li>
      </ul>

      <div class="flex justify-end pt-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.close') }}
        </ButtonCl>
      </div>
    </div>
  </DialogCl>

  <AutoTagRuleEditDialog
    v-model:open="editorOpen"
    :collection-id="collectionId"
    :rule="editingRule"
  />
</template>
