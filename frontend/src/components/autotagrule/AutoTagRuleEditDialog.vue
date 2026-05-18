<script setup lang="ts">
import type { AutoTagRuleJson } from '@/api/generated'
import { DialogCl, DialogFooterCl, FormFieldCl, HelpPopoverCl, InputCl } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { autoTagRuleSaveSchema } from '@/schemas/autoTagRule'
import { useAutoTagRuleStore } from '@/stores/autoTagRule'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { Copy } from '@lucide/vue'
import { useForm } from 'vee-validate'
import { computed, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const ruleStore = useAutoTagRuleStore()
const notification = useNotificationStore()

interface Props {
  collectionId: string
  rule?: AutoTagRuleJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(autoTagRuleSaveSchema(t)),
  initialValues: {
    collectionId: props.collectionId,
    pattern: '',
    tagNames: '',
    description: '',
    enabled: true,
  },
})

const [pattern] = defineField('pattern')
const [tagNames] = defineField('tagNames')
const [description] = defineField('description')
const [enabled] = defineField('enabled')

const testUrl = ref('')
const helpPopover = ref<InstanceType<typeof HelpPopoverCl> | null>(null)

const isEditing = computed(() => !!props.rule)

useFormDialog(toRef(props, 'open'), () => {
  if (props.rule) {
    resetForm({
      values: {
        collectionId: props.collectionId,
        pattern: props.rule.data.pattern,
        tagNames: props.rule.data.tagNames,
        description: props.rule.data.description ?? '',
        enabled: props.rule.data.enabled,
      },
    })
  } else {
    resetForm({
      values: {
        collectionId: props.collectionId,
        pattern: '',
        tagNames: '',
        description: '',
        enabled: true,
      },
    })
  }
  testUrl.value = ''
})

const compiledRegex = computed<{ regex: RegExp | null; error: string | null }>(() => {
  if (!pattern.value) return { regex: null, error: null }
  try {
    return { regex: new RegExp(pattern.value), error: null }
  } catch (e) {
    return { regex: null, error: e instanceof Error ? e.message : String(e) }
  }
})

const matchInfo = computed(() => {
  const { regex } = compiledRegex.value
  if (!regex || !testUrl.value) return null
  const m = regex.exec(testUrl.value)
  return m ? { matched: true, segment: m[0] } : { matched: false, segment: '' }
})

const tagPreview = computed(() =>
  (tagNames.value ?? '')
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter((s) => s.length > 0),
)

const examples = [
  {
    label: 'GitHub PRs',
    pattern: String.raw`^https://github\.com/.+/pull/\d+`,
    tags: 'pr, github',
  },
  {
    label: 'Confluence wiki',
    pattern: String.raw`^https://[^/]+\.atlassian\.net/wiki/`,
    tags: 'wiki',
  },
  { label: 'k8s subdomains', pattern: String.raw`^https://[^/]+\.k8s\.acme\.io/`, tags: 'k8s' },
  {
    label: 'localhost custom port',
    pattern: String.raw`^https?://localhost:(?!80|443)\d+`,
    tags: 'local, port',
  },
  {
    label: 'YouTube watch',
    pattern: String.raw`^https://(www\.)?youtube\.com/watch\?`,
    tags: 'video, youtube',
  },
]

function copyExample(p: string) {
  void navigator.clipboard.writeText(p)
}

function applyExample(ex: { pattern: string; tags: string }) {
  pattern.value = ex.pattern
  tagNames.value = ex.tags
  helpPopover.value?.close()
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (props.rule) {
      await ruleStore.updateRule(props.rule.id, values)
    } else {
      await ruleStore.createRule(values)
    }
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('autoTagRule.saveError'))
  }
})

watch(testUrl, () => {})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>
      <span class="flex items-center gap-2">
        {{ isEditing ? t('autoTagRule.editTitle') : t('autoTagRule.createTitle') }}
        <HelpPopoverCl ref="helpPopover" :aria-label="t('autoTagRule.helpAria')" width="26rem">
          <p class="font-medium mb-2">{{ t('autoTagRule.helpHeader') }}</p>
          <p class="mb-2 text-muted-foreground">{{ t('autoTagRule.helpSyntax') }}</p>
          <p class="font-medium mb-1">{{ t('autoTagRule.helpExamples') }}</p>
          <div class="space-y-2">
            <div v-for="ex in examples" :key="ex.label" class="border-t border-border pt-1.5">
              <p class="font-medium mb-0.5">{{ ex.label }}</p>
              <div class="flex items-start gap-1">
                <code class="font-mono bg-muted px-1 rounded text-[11px] break-all flex-1">{{
                  ex.pattern
                }}</code>
                <button
                  type="button"
                  class="shrink-0 text-primary hover:underline"
                  @click="copyExample(ex.pattern)"
                  :title="t('autoTagRule.copyPattern')"
                >
                  <Copy class="h-3 w-3" />
                </button>
                <button
                  type="button"
                  class="shrink-0 text-primary hover:underline"
                  @click="applyExample(ex)"
                >
                  {{ t('autoTagRule.useExample') }}
                </button>
              </div>
            </div>
          </div>
        </HelpPopoverCl>
      </span>
    </template>

    <form id="auto-tag-rule-form" class="space-y-4" @submit.prevent="onSubmit">
      <!--      Regex pattern-->
      <FormFieldCl
        :label="t('autoTagRule.pattern')"
        for-id="auto-tag-rule-pattern"
        :error="errors.pattern"
        required
      >
        <input
          id="auto-tag-rule-pattern"
          v-model="pattern"
          type="text"
          autocomplete="off"
          spellcheck="false"
          maxlength="2000"
          data-testid="auto-tag-rule-pattern"
          :placeholder="t('autoTagRule.patternPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 font-mono text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>
      <!--Regex tester-->
      <div class="space-y-1">
        <label class="text-xs font-medium text-muted-foreground" for="auto-tag-rule-test">
          {{ t('autoTagRule.testUrl') }}
        </label>
        <input
          id="auto-tag-rule-test"
          v-model="testUrl"
          type="text"
          autocomplete="off"
          spellcheck="false"
          data-testid="auto-tag-rule-test"
          placeholder="https://github.com/foo/bar/pull/42"
          class="flex h-8 w-full rounded-md border border-input bg-transparent px-3 py-1 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
        <p
          v-if="matchInfo"
          class="text-xs"
          :class="matchInfo.matched ? 'text-emerald-600' : 'text-destructive'"
        >
          <template v-if="matchInfo.matched">
            ✓ {{ t('autoTagRule.testMatched') }}:
            <code class="font-mono">{{ matchInfo.segment }}</code>
          </template>
          <template v-else>✗ {{ t('autoTagRule.testNotMatched') }}</template>
        </p>
      </div>
      <!-- Tag names-->
      <FormFieldCl
        :label="t('autoTagRule.tagNames')"
        for-id="auto-tag-rule-tags"
        :error="errors.tagNames"
        required
      >
        <InputCl
          id="auto-tag-rule-tags"
          v-model="tagNames"
          type="text"
          maxlength="2000"
          data-testid="auto-tag-rule-tags"
          :placeholder="t('autoTagRule.tagNamesPlaceholder')"
        />
        <div v-if="tagPreview.length > 0" class="mt-1.5 flex flex-wrap gap-1">
          <span
            v-for="name in tagPreview"
            :key="name"
            class="inline-flex items-center rounded-full border border-dashed border-input px-2 py-0.5 text-[11px]"
          >
            {{ name }}
          </span>
        </div>
        <p class="text-[11px] text-muted-foreground mt-1">{{ t('autoTagRule.tagNamesHelp') }}</p>
      </FormFieldCl>
      <!--Description-->
      <FormFieldCl
        :label="t('autoTagRule.description')"
        for-id="auto-tag-rule-desc"
        :error="errors.description"
      >
        <input
          id="auto-tag-rule-desc"
          v-model="description"
          type="text"
          maxlength="255"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>

      <label class="flex items-center gap-2 text-sm">
        <input v-model="enabled" type="checkbox" class="h-4 w-4" />
        {{ t('autoTagRule.enabled') }}
      </label>
    </form>

    <template #footer>
      <DialogFooterCl
        submit-form="auto-tag-rule-form"
        :submit-label="isEditing ? t('common.save') : t('common.create')"
        :submitting="isSubmitting"
        submit-testid="auto-tag-rule-submit"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogCl>
</template>
