<script setup lang="ts">
import { useAiTagSuggestions } from '@/composables/useAiTagSuggestions'
import {
  buildSuggestionGroups,
  type RuleSuggestion,
} from '@/lib/aiTagSuggestions'
import { compileCustomRules, suggestAllTagNames } from '@/lib/tag-suggester'
import { useAutoTagRuleStore } from '@/stores/autoTagRule'
import { useNotificationStore } from '@/stores/notification'
import { useTagStore } from '@/stores/tag'
import { RefreshCw, ShieldCheck, Sparkles, Zap } from '@lucide/vue'
import { computed, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * "Suggested tags" section (AI Suggested Tags handoff). Merges two suggestion
 * sources in one grouped panel: rule-based chips (instant, client-side) under
 * "From your rules", and model-proposed chips (async, on-device) under "AI
 * suggestions". Chips are toggleable and pre-selected; nothing is applied until
 * Accept. On accept the selected tags are emitted to the parent to fold into the
 * bookmark's Tags field.
 */
const props = defineProps<{
  open: boolean
  isEdit: boolean
  collectionId: string
  title: string | undefined
  url: string | undefined
  description: string | undefined
  appliedTagIds: Set<string>
}>()

const emit = defineEmits<{ 'add-tags': [ids: string[]] }>()

const { t } = useI18n()
const tagStore = useTagStore()
const autoTagRuleStore = useAutoTagRuleStore()
const notification = useNotificationStore()

const urlRef = toRef(props, 'url')
const titleRef = toRef(props, 'title')
const descriptionRef = toRef(props, 'description')
const collectionIdRef = toRef(props, 'collectionId')

const { aiState, aiSuggestions, warmUp, regenerate, retrieve, markHandled, reset } =
  useAiTagSuggestions({
    collectionId: collectionIdRef,
    title: titleRef,
    url: urlRef,
    description: descriptionRef,
  })

// --- Rule suggestions (client-side, instant) ---
const compiledCustom = computed(() =>
  compileCustomRules(
    autoTagRuleStore.rules.map((r) => ({
      pattern: r.data.pattern,
      tagNames: r.data.tagNames,
      enabled: r.data.enabled,
    })),
  ),
)

const ruleSuggestions = computed<RuleSuggestion[]>(() => {
  const names = suggestAllTagNames(props.url ?? '', compiledCustom.value)
  return names.map((name) => {
    const lower = name.toLowerCase()
    const existing = tagStore.tags.find((tag) => tag.data.name.toLowerCase() === lower)
    return { name, existingTagId: existing?.id ?? null }
  })
})

const groups = computed(() =>
  buildSuggestionGroups(
    ruleSuggestions.value,
    aiSuggestions.value,
    props.appliedTagIds,
    tagStore.tags,
  ),
)

// --- Selection (pre-select everything; reset when the visible set changes) ---
const selectedRuleNames = ref<Set<string>>(new Set())
const selectedAiIds = ref<Set<string>>(new Set())

const visibleSignature = computed(() =>
  [
    ...groups.value.rules.map((c) => `r:${c.name}`),
    ...groups.value.ai.map((c) => `a:${c.id}`),
  ].join('|'),
)

watch(visibleSignature, () => {
  selectedRuleNames.value = new Set(groups.value.rules.map((c) => c.name))
  selectedAiIds.value = new Set(groups.value.ai.map((c) => c.id))
})

function toggleRule(name: string) {
  const next = new Set(selectedRuleNames.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selectedRuleNames.value = next
}

function toggleAi(id: string) {
  const next = new Set(selectedAiIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedAiIds.value = next
}

const selectedCount = computed(
  () => selectedRuleNames.value.size + selectedAiIds.value.size,
)

const hasAnySuggestion = computed(
  () => groups.value.rules.length > 0 || groups.value.ai.length > 0,
)

// --- Accept / dismiss state ---
const applying = ref(false)
const justApplied = ref(false)

// --- Lifecycle: reset + warm-up on open ---
watch(
  () => props.open,
  (open) => {
    if (!open) return
    const handled = props.url?.trim() ? props.url.trim() : null
    reset(handled)
    selectedRuleNames.value = new Set()
    selectedAiIds.value = new Set()
    justApplied.value = false
    warmUp()
  },
  { immediate: true },
)

async function accept() {
  if (applying.value || selectedCount.value === 0) return
  applying.value = true
  const ids: string[] = []
  const failed: string[] = []
  try {
    for (const chip of groups.value.rules) {
      if (!selectedRuleNames.value.has(chip.name)) continue
      if (chip.existingTagId) {
        ids.push(chip.existingTagId)
        continue
      }
      try {
        const created = await tagStore.createTag({
          collectionId: props.collectionId,
          name: chip.name,
        })
        ids.push(created.id)
      } catch {
        failed.push(chip.name)
      }
    }
    for (const chip of groups.value.ai) {
      if (selectedAiIds.value.has(chip.id)) ids.push(chip.id)
    }
    if (ids.length > 0) emit('add-tags', ids)
    justApplied.value = true
    markHandled()
    if (failed.length > 0) {
      notification.error(t('bookmark.tagSuggestionError'))
    }
  } finally {
    applying.value = false
  }
}

function dismiss() {
  justApplied.value = false
  markHandled()
}

function onRetrieve() {
  justApplied.value = false
  retrieve()
}
</script>

<template>
  <div data-testid="suggested-tags-section" class="ai-suggest">
    <!-- Collapsed: slim retrieve pill -->
    <button
      v-if="aiState === 'collapsed'"
      type="button"
      class="ai-retrieve"
      data-testid="ai-retrieve-pill"
      @click="onRetrieve"
    >
      <Sparkles :size="13" />
      <span>{{ t('bookmark.retrieveSuggestions') }}</span>
      <span class="ai-retrieve-meta">· {{ t('bookmark.aiOnDevice') }}</span>
      <span v-if="justApplied" class="ai-applied">{{ t('bookmark.suggestionsApplied') }}</span>
    </button>

    <!-- Expanded section -->
    <section v-else class="ai-panel">
      <header class="ai-head">
        <span class="ai-head-title">
          <Sparkles :size="14" class="ai-spark" />
          {{ t('bookmark.suggestedTags') }}
          <span v-if="selectedCount > 0" class="ai-count">{{ selectedCount }}</span>
        </span>
        <span class="ai-privacy">
          <ShieldCheck :size="12" />
          {{ t('bookmark.aiOnDevice') }}
        </span>
      </header>

      <!-- From your rules -->
      <div v-if="groups.rules.length > 0" class="ai-group">
        <div class="ai-group-label">{{ t('bookmark.fromYourRules') }}</div>
        <div class="ai-chips">
          <button
            v-for="chip in groups.rules"
            :key="chip.name"
            type="button"
            class="chip chip--rule"
            :class="{ 'chip--off': !selectedRuleNames.has(chip.name) }"
            :aria-pressed="selectedRuleNames.has(chip.name)"
            :data-testid="`suggested-tag-${chip.name}`"
            @click="toggleRule(chip.name)"
          >
            <Zap :size="11" class="chip-src" />
            <span class="chip-dot" :style="{ background: chip.color ?? 'var(--color-muted-foreground)' }" />
            <span>{{ chip.name }}</span>
            <span v-if="!chip.existingTagId" class="chip-new">{{ t('bookmark.suggestionWillCreate') }}</span>
          </button>
        </div>
      </div>

      <!-- AI suggestions -->
      <div class="ai-group">
        <div class="ai-group-head">
          <span class="ai-group-label ai-group-label--ai">{{ t('bookmark.aiSuggestionsGroup') }}</span>
          <button
            v-if="aiState === 'ok'"
            type="button"
            class="ai-regen"
            data-testid="ai-regenerate"
            @click="regenerate"
          >
            <RefreshCw :size="11" />
            {{ t('bookmark.regenerate') }}
          </button>
        </div>

        <!-- loading: shimmer skeleton pills -->
        <div
          v-if="aiState === 'loading'"
          class="ai-chips"
          aria-busy="true"
          role="status"
          :aria-label="t('bookmark.aiGenerating')"
          data-testid="ai-loading"
        >
          <span v-for="i in 3" :key="i" class="chip chip--shimmer" :style="{ width: `${68 + i * 14}px` }" />
        </div>

        <!-- idle: manual trigger -->
        <div v-else-if="aiState === 'idle'" class="ai-idle">
          <button
            type="button"
            class="ai-idle-btn"
            data-testid="ai-suggest-btn"
            :disabled="!url"
            @click="onRetrieve"
          >
            <Sparkles :size="13" />
            {{ t('bookmark.suggestWithAi') }}
          </button>
          <p class="ai-idle-hint">{{ t('bookmark.aiHint') }}</p>
        </div>

        <!-- empty -->
        <p v-else-if="aiState === 'empty'" class="ai-empty" data-testid="ai-empty">
          {{ t('bookmark.aiEmpty') }}
        </p>

        <!-- ok: chips -->
        <div v-else class="ai-chips">
          <button
            v-for="chip in groups.ai"
            :key="chip.id"
            type="button"
            class="chip chip--ai"
            :class="{ 'chip--off': !selectedAiIds.has(chip.id) }"
            :aria-pressed="selectedAiIds.has(chip.id)"
            :data-testid="`ai-suggested-tag-${chip.name}`"
            @click="toggleAi(chip.id)"
          >
            <Sparkles :size="11" class="chip-src chip-src--ai" />
            <span class="chip-dot" :style="{ background: chip.color }" />
            <span>{{ chip.name }}</span>
          </button>
          <span v-if="groups.ai.length === 0" class="ai-empty">{{ t('bookmark.aiEmpty') }}</span>
        </div>
      </div>

      <p class="ai-foot-note">{{ t('bookmark.aiFootnote') }}</p>

      <footer v-if="hasAnySuggestion" class="ai-foot">
        <button type="button" class="ai-dismiss" data-testid="dismiss-suggestions-btn" @click="dismiss">
          {{ t('bookmark.dismiss') }}
        </button>
        <button
          type="button"
          class="ai-accept"
          data-testid="accept-suggestions-btn"
          :disabled="selectedCount === 0 || applying"
          @click="accept"
        >
          {{ t('bookmark.acceptN', { count: selectedCount }) }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.ai-suggest {
  min-height: 2.25rem;
}

/* Collapsed retrieve pill */
.ai-retrieve {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border-radius: 9999px;
  border: 1px solid color-mix(in oklab, var(--ai) 45%, var(--color-border));
  background: color-mix(in oklab, var(--ai) 10%, transparent);
  color: var(--color-foreground);
  font-size: 12px;
  cursor: pointer;
}
.ai-retrieve:hover {
  background: color-mix(in oklab, var(--ai) 16%, transparent);
}
.ai-retrieve-meta {
  color: var(--color-muted-foreground);
}
.ai-applied {
  margin-left: 8px;
  color: var(--ai-strong);
  font-weight: 600;
}

/* Panel */
.ai-panel {
  border-radius: 12px;
  border: 1px solid color-mix(in oklab, var(--ai) 35%, var(--color-border));
  background: color-mix(in oklab, var(--ai) 6%, transparent);
  padding: 10px 12px;
}
.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ai-head-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}
.ai-spark {
  color: var(--ai-strong);
}
.ai-count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9999px;
  background: color-mix(in oklab, var(--ai) 25%, transparent);
  color: var(--ai-strong);
  font-size: 11px;
  font-weight: 700;
}
.ai-privacy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-muted-foreground);
}

.ai-group {
  margin-top: 10px;
}
.ai-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ai-group-label {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-muted-foreground);
}
.ai-group-label--ai {
  color: color-mix(in oklab, var(--ai-strong) 80%, var(--color-muted-foreground));
}
.ai-regen {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--ai-strong);
  font-size: 11px;
  cursor: pointer;
}
.ai-regen:hover {
  text-decoration: underline;
}

.ai-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  border-radius: 9999px;
  border: 1px solid var(--color-border);
  background: var(--color-secondary);
  color: var(--color-foreground);
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.12s ease;
}
.chip--rule {
  border-color: color-mix(in oklab, var(--color-primary) 55%, var(--color-border));
}
.chip--ai {
  border-color: color-mix(in oklab, var(--ai) 55%, var(--color-border));
  background: color-mix(in oklab, var(--ai) 8%, var(--color-secondary));
}
.chip--off {
  opacity: 0.55;
}
.chip-src {
  color: var(--color-primary);
}
.chip-src--ai {
  color: var(--ai-strong);
}
.chip-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  flex-shrink: 0;
}
.chip-new {
  color: var(--color-muted-foreground);
  font-size: 10.5px;
}

/* Shimmer skeleton */
.chip--shimmer {
  height: 28px;
  border-radius: 9999px;
  border: 1px dashed color-mix(in oklab, var(--ai) 50%, var(--color-border));
  background: linear-gradient(
    100deg,
    transparent 30%,
    color-mix(in oklab, var(--ai) 22%, transparent) 50%,
    transparent 70%
  );
  background-size: 200% 100%;
  animation: ai-shimmer 1.15s ease-in-out infinite;
}
@keyframes ai-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.ai-idle {
  margin-top: 6px;
}
.ai-idle-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border-radius: 9999px;
  border: 1px solid color-mix(in oklab, var(--ai) 45%, var(--color-border));
  background: color-mix(in oklab, var(--ai) 10%, transparent);
  color: var(--color-foreground);
  font-size: 12px;
  cursor: pointer;
}
.ai-idle-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.ai-idle-hint {
  margin-top: 5px;
  font-size: 11px;
  color: var(--color-muted-foreground);
}
.ai-empty {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-muted-foreground);
}

.ai-foot-note {
  margin-top: 10px;
  font-size: 11px;
  color: var(--color-muted-foreground);
}
.ai-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.ai-dismiss {
  height: 28px;
  padding: 0 12px;
  border-radius: 7px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-foreground);
  font-size: 12px;
  cursor: pointer;
}
.ai-dismiss:hover {
  background: var(--color-secondary);
}
.ai-accept {
  height: 28px;
  padding: 0 14px;
  border-radius: 7px;
  border: none;
  background: var(--ai-strong);
  color: white;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.ai-accept:disabled {
  background: var(--color-muted);
  color: var(--color-muted-foreground);
  cursor: default;
}

@media (prefers-reduced-motion: reduce) {
  .chip--shimmer {
    animation: none;
  }
}
</style>
