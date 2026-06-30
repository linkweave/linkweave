import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { config } from '@/api'
import { BookmarkAutoTagResourceApi, type TagJson } from '@/api/generated'

const api = new BookmarkAutoTagResourceApi(config)

/** Debounce before auto-firing after the last title/URL edit (handoff: ~700ms). */
const AUTO_FIRE_DEBOUNCE_MS = 700

/**
 * Whether the model call fires automatically once Title + URL are present.
 * The handoff flags this as a behaviour to confirm with the team (cost/latency
 * of running gemma 2B on every new bookmark). Flip to `false` to ship
 * idle-by-default (manual "Suggest tags with AI" button only).
 */
const AUTO_FIRE = true

type AiState = 'idle' | 'loading' | 'ok' | 'empty' | 'collapsed'

export interface UseAiTagSuggestionsOptions {
  collectionId: Ref<string>
  title: Ref<string | undefined>
  url: Ref<string | undefined>
  description: Ref<string | undefined>
}

/**
 * Drives the AI half of the Suggested tags section: an on-demand call to the
 * local model that returns existing collection tags. Non-blocking and
 * best-effort — failures collapse to the empty state, never throw. Auto-fires
 * (debounced) when Title + URL are present and the URL hasn't been handled yet;
 * re-arms when the URL changes.
 */
export function useAiTagSuggestions(opts: UseAiTagSuggestionsOptions) {
  const { collectionId, title, url, description } = opts

  const aiState = ref<AiState>('idle')
  const aiSuggestions = ref<TagJson[]>([])
  const lastHandledUrl = ref<string | null>(null)

  let abort: AbortController | null = null
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  function clearDebounce() {
    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  async function runAi(): Promise<void> {
    const u = url.value?.trim()
    const cid = collectionId.value
    if (!u || !cid) return

    abort?.abort()
    const controller = new AbortController()
    abort = controller

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
      aiState.value = aiSuggestions.value.length > 0 ? 'ok' : 'empty'
    } catch {
      if (controller.signal.aborted) return // superseded by a newer run
      // Best-effort (BR-077): surface as empty, never disrupt the dialog.
      aiSuggestions.value = []
      aiState.value = 'empty'
    } finally {
      if (abort === controller) abort = null
    }
  }

  /** Silent warm-up so the first real run isn't paying cold-start latency. */
  function warmUp(): void {
    const cid = collectionId.value
    if (!cid) return
    void api.apiCollectionsCollectionIdAutotagWarmUpPost({ collectionId: cid }).catch(() => {
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

  /** Mark the current URL as handled and collapse (after Accept / Dismiss). */
  function markHandled(): void {
    lastHandledUrl.value = url.value?.trim() ?? null
    aiState.value = 'collapsed'
    clearDebounce()
    abort?.abort()
  }

  /**
   * Reset for a freshly-opened dialog. `handledUrl` pre-marks a URL as already
   * handled so an unchanged Edit URL doesn't auto-fire on open (the user can
   * still ask via the pill); pass null for a fresh Add.
   */
  function reset(handledUrl: string | null): void {
    clearDebounce()
    abort?.abort()
    aiState.value = 'idle'
    aiSuggestions.value = []
    lastHandledUrl.value = handledUrl
  }

  // Re-arm when the URL changes to a value we haven't handled.
  watch(url, (next) => {
    const u = next?.trim() ?? ''
    if (u !== (lastHandledUrl.value ?? '') && aiState.value !== 'loading') {
      aiState.value = 'idle'
      aiSuggestions.value = []
    }
  })

  // Debounced auto-fire on title/URL settle.
  watch([title, url], () => {
    if (!AUTO_FIRE) return
    clearDebounce()
    debounceTimer = setTimeout(() => {
      const u = url.value?.trim()
      const ti = title.value?.trim()
      if (
        aiState.value === 'idle' &&
        u &&
        ti &&
        u !== (lastHandledUrl.value ?? '')
      ) {
        void runAi()
      }
    }, AUTO_FIRE_DEBOUNCE_MS)
  })

  onBeforeUnmount(() => {
    clearDebounce()
    abort?.abort()
  })

  return { aiState, aiSuggestions, warmUp, regenerate, retrieve, markHandled, reset }
}
