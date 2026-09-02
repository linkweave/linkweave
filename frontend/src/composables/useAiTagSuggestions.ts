import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { config } from '@/api'
import {
  BookmarkAutoTagResourceApi,
  SuggestionStatusJson,
  type AutotagLLMProviderJson,
  type TagJson,
} from '@/api/generated'

const api = new BookmarkAutoTagResourceApi(config)

/** Debounce before auto-firing after the last title/URL edit (handoff: ~700ms). */
const AUTO_FIRE_DEBOUNCE_MS = 700

/**
 * Client-side ceiling on a suggestion request, used only until the server tells
 * us its own budget (UC-108 BR-108-1). The server aborts at
 * `linkweave.autotag.suggest-timeout-ms` and reports that value on the warm-up
 * response; this is the fallback for the window before warm-up resolves, and for
 * a warm-up that never does. A little headroom over the server budget so a
 * server that answers right at its limit is still heard.
 */
const FALLBACK_ABORT_MS = 10_000

/**
 * States the section can be in.
 *
 * `empty` and `unavailable` are deliberately separate (BR-108-5): the model
 * having no confident suggestion is a real answer, the model not answering is
 * not, and collapsing both into `empty` is what made a stalled model look like a
 * working one that simply had no ideas.
 */
type AiState = 'idle' | 'loading' | 'ok' | 'empty' | 'unavailable' | 'preparing' | 'collapsed'

export interface UseAiTagSuggestionsOptions {
  collectionId: Ref<string>
  title: Ref<string | undefined>
  url: Ref<string | undefined>
  description: Ref<string | undefined>
}

/** Maps the server's status onto the state the section renders. */
function stateForStatus(status: SuggestionStatusJson, hasTags: boolean): AiState {
  switch (status) {
    case SuggestionStatusJson.Ok:
      return hasTags ? 'ok' : 'empty'
    case SuggestionStatusJson.Preparing:
      return 'preparing'
    case SuggestionStatusJson.Unavailable:
      return 'unavailable'
    // A switched-off feature is not a degraded one, and saying "unavailable"
    // would invite a retry that can never succeed. Collapse it quietly instead.
    case SuggestionStatusJson.Disabled:
      return 'collapsed'
    default:
      return 'empty'
  }
}

/**
 * Drives the AI half of the Suggested tags section: an on-demand call to the
 * configured model that returns existing collection tags. Non-blocking and
 * best-effort — a failure degrades the section, it never disrupts the dialog or
 * delays a save. Auto-fires (debounced) when Title + URL are present and the URL
 * hasn't been handled yet, if the server has auto-fire enabled (BR-108-7);
 * re-arms when the content changes.
 */
export function useAiTagSuggestions(opts: UseAiTagSuggestionsOptions) {
  const { collectionId, title, url, description } = opts

  const aiState = ref<AiState>('idle')
  const aiSuggestions = ref<TagJson[]>([])
  const provider = ref<AutotagLLMProviderJson | null>(null)
  // Signature of the content (title + url + description) we last ran for — all
  // three feed the request, so a change to any means the suggestions are stale
  // and we should re-arm. Null means "nothing handled yet" (fresh Add).
  const lastRunKey = ref<string | null>(null)

  let abort: AbortController | null = null
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * Signature of the three fields, joined on a character that cannot occur in
   * any of them. A printable separator would let different content collide —
   * title "a b" with an empty URL would key the same as title "a" with URL "b" —
   * and a collision here means the section fails to re-arm for content it has
   * not actually run for.
   */
  function currentKey(): string {
    return [
      title.value?.trim() ?? '',
      url.value?.trim() ?? '',
      description.value?.trim() ?? '',
    ].join('\u0000')
  }

  function clearDebounce() {
    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  /**
   * The budget this request gets before the client gives up on its own. The
   * server aborts at its configured budget, but a server that is itself wedged
   * (or a connection that dies quietly) would otherwise leave the dialog
   * spinning indefinitely, so the client keeps its own clock.
   */
  /**
   * Whether AI suggestions run here at all (UC-112 BR-112-7). Sourced from the
   * warm-up response — the server's answer, combining the operator's flag with
   * the collection's — so `null` means "not known yet" and is deliberately not
   * treated as enabled: an affordance that appears and then vanishes is worse
   * than one that appears a beat late.
   */
  const aiEnabled = computed(() => provider.value?.enabled === true)

  function abortBudgetMs(): number {
    const serverBudget = provider.value?.suggestTimeoutMs
    return serverBudget && serverBudget > 0 ? serverBudget + 2_000 : FALLBACK_ABORT_MS
  }

  async function runAi(): Promise<void> {
    const u = url.value?.trim()
    const cid = collectionId.value
    if (!u || !cid) return
    // The server refuses anyway (BR-112-4); not asking keeps a switched-off
    // collection from generating request noise at all.
    if (!aiEnabled.value) return

    abort?.abort()
    const controller = new AbortController()
    abort = controller
    const budgetTimer = setTimeout(() => controller.abort(), abortBudgetMs())

    // Record what we're running for so an unchanged content set doesn't re-fire,
    // and a later edit to any field does.
    lastRunKey.value = currentKey()
    aiState.value = 'loading'
    aiSuggestions.value = []
    try {
      const res = await api.apiCollectionsCollectionIdAutotagSuggestTagsPost(
        {
          collectionId: cid,
          suggestTagsJson: {
            title: title.value ?? undefined,
            url: u,
            description: description.value ?? undefined,
          },
        },
        { signal: controller.signal },
      )
      if (controller.signal.aborted) return
      aiSuggestions.value = res.tagList ?? []
      aiState.value = stateForStatus(res.status, aiSuggestions.value.length > 0)
    } catch {
      if (controller.signal.aborted && abort !== controller) return // superseded by a newer run
      // Best-effort (BR-077): the section says the feature is unavailable and
      // stops there. No dialog, no blocking, no automatic retry.
      aiSuggestions.value = []
      aiState.value = 'unavailable'
    } finally {
      clearTimeout(budgetTimer)
      if (abort === controller) abort = null
    }
  }

  /**
   * Silent warm-up so the first real run isn't paying cold-start latency. Also
   * captures the active provider/model so the badge labels itself, and the
   * operator's auto-fire and budget settings (BR-108-7, BR-108-1). The response
   * is config-derived and returns immediately, even while the model preloads.
   */
  function warmUp(): void {
    const cid = collectionId.value
    if (!cid) return
    void api
      .apiCollectionsCollectionIdAutotagWarmUpPost({ collectionId: cid })
      .then((info) => {
        provider.value = info
      })
      .catch(() => {
        // best-effort; warm-up failure is invisible to the user
      })
  }

  /** Re-run for the current title/URL without collapsing (Regenerate link). */
  function regenerate(): void {
    clearDebounce()
    void runAi()
  }

  /** Manual trigger (idle "Suggest tags with AI" button, or the retrieve pill). */
  function retrieve(): void {
    clearDebounce()
    void runAi()
  }

  /** Mark the current content as handled and collapse (after Accept / Dismiss). */
  function markHandled(): void {
    lastRunKey.value = currentKey()
    aiState.value = 'collapsed'
    clearDebounce()
    abort?.abort()
  }

  /**
   * Reset for a freshly-opened dialog. `prefilled` pre-marks the current content
   * as already handled so an unchanged Edit dialog doesn't auto-fire on open (the
   * user can still ask via the pill); pass false for a fresh Add.
   */
  function reset(prefilled: boolean): void {
    clearDebounce()
    abort?.abort()
    aiState.value = 'idle'
    aiSuggestions.value = []
    lastRunKey.value = prefilled ? currentKey() : null
  }

  // Re-arm when any field feeding the request changes to content we haven't run
  // for — drops stale suggestions and lets the auto-fire pick it up. Skipped
  // while a run is in flight or after an explicit dismiss (collapsed pill).
  watch([title, url, description], () => {
    if (aiState.value === 'loading' || aiState.value === 'collapsed') return
    if (currentKey() !== lastRunKey.value) {
      aiState.value = 'idle'
      aiSuggestions.value = []
    }
  })

  // Debounced auto-fire once Title + URL are present and the content changed.
  // Gated on the server's setting (BR-108-7), which also means nothing fires
  // until warm-up has answered — the safe direction, since a host that has
  // turned auto-fire off must see zero model calls it did not ask for.
  watch([title, url, description], () => {
    clearDebounce()
    debounceTimer = setTimeout(() => {
      if (provider.value?.autoFire !== true) return
      const u = url.value?.trim()
      const ti = title.value?.trim()
      if (aiState.value === 'idle' && u && ti && currentKey() !== lastRunKey.value) {
        void runAi()
      }
    }, AUTO_FIRE_DEBOUNCE_MS)
  })

  onBeforeUnmount(() => {
    clearDebounce()
    abort?.abort()
  })

  return {
    aiState,
    aiSuggestions,
    provider,
    aiEnabled,
    warmUp,
    regenerate,
    retrieve,
    markHandled,
    reset,
  }
}
