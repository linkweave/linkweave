// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, ref } from 'vue'
import { SuggestionStatusJson, type SuggestedTagsResultJson } from '@/api/generated'
import { useAiTagSuggestions } from './useAiTagSuggestions'

// Hoisted: `vi.mock` factories are lifted above ordinary const declarations, so
// plain module-level spies would not exist yet when the factory runs.
const { suggestSpy, warmUpSpy } = vi.hoisted(() => ({
  suggestSpy: vi.fn(),
  warmUpSpy: vi.fn(),
}))

// The composable holds a module-level API client, so the class itself is mocked
// rather than the instance.
vi.mock('@/api/generated', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/generated')>()
  return {
    ...actual,
    BookmarkAutoTagResourceApi: class {
      apiCollectionsCollectionIdAutotagSuggestTagsPost = suggestSpy
      apiCollectionsCollectionIdAutotagWarmUpPost = warmUpSpy
    },
  }
})

function result(status: SuggestionStatusJson, tagList: unknown[] = []): SuggestedTagsResultJson {
  return { tagList, status } as SuggestedTagsResultJson
}

function provider(autoFire: boolean, suggestTimeoutMs = 8000, enabled = true) {
  return {
    provider: 'ollama',
    model: 'gemma2:2b',
    onDevice: true,
    enabled,
    autoFire,
    suggestTimeoutMs,
  }
}

/**
 * Mounts the composable inside a throwaway component so its watchers and
 * `onBeforeUnmount` run against a real instance, as they do in the dialog. Every
 * app is tracked and torn down after the test so a leftover debounce timer
 * cannot fire a request into the next one.
 */
const mounted: { unmount: () => void }[] = []

function mountComposable() {
  const title = ref<string | undefined>('')
  const url = ref<string | undefined>('')
  const description = ref<string | undefined>(undefined)
  let api!: ReturnType<typeof useAiTagSuggestions>

  const app = createApp({
    setup() {
      api = useAiTagSuggestions({
        collectionId: ref('c-1'),
        title,
        url,
        description,
      })
      return () => null
    },
  })
  app.mount(document.createElement('div'))
  const handle = { unmount: () => app.unmount() }
  mounted.push(handle)
  return { unmount: handle.unmount, title, url, description, api: () => api }
}

/**
 * Fills in the fields a suggestion needs, without letting the auto-fire debounce
 * elapse — for tests that drive the request explicitly.
 */
async function fillContent(
  fields: { title: ReturnType<typeof ref<string | undefined>>; url: ReturnType<typeof ref<string | undefined>> },
) {
  fields.title.value = 'Async Rust'
  fields.url.value = 'https://example.com/rust'
  await nextTick()
}

/** Types a title and URL, then lets the auto-fire debounce elapse. */
async function typeBookmark(
  fields: { title: ReturnType<typeof ref<string | undefined>>; url: ReturnType<typeof ref<string | undefined>> },
) {
  fields.title.value = 'Async Rust'
  fields.url.value = 'https://example.com/rust'
  await nextTick()
  await vi.advanceTimersByTimeAsync(800)
  await nextTick()
}

describe('useAiTagSuggestions', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    suggestSpy.mockReset()
    warmUpSpy.mockReset()
    warmUpSpy.mockResolvedValue(provider(true))
  })

  afterEach(() => {
    while (mounted.length) mounted.pop()?.unmount()
    vi.useRealTimers()
  })

  it('auto-fires once the server says auto-fire is on', async () => {
    // ARRANGE
    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Ok, [{ id: 't-1' }]))
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)

    // ACT
    await typeBookmark(ctx)

    // ASSERT
    expect(suggestSpy).toHaveBeenCalledTimes(1)
    expect(ctx.api().aiState.value).toBe('ok')
  })

  it('makes zero model calls until asked when the operator disabled auto-fire', async () => {
    // ARRANGE — BR-108-7 / acceptance criterion 5.
    warmUpSpy.mockResolvedValue(provider(false))
    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Ok, [{ id: 't-1' }]))
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)

    // ACT
    await typeBookmark(ctx)

    // ASSERT
    expect(suggestSpy).not.toHaveBeenCalled()
    expect(ctx.api().aiState.value).toBe('idle')

    // The explicit button still works.
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)
    expect(suggestSpy).toHaveBeenCalledTimes(1)
  })

  it('does not auto-fire before warm-up has reported the setting', async () => {
    // ARRANGE — warm-up never resolves, so the setting is unknown.
    warmUpSpy.mockReturnValue(new Promise(() => {}))
    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Ok, [{ id: 't-1' }]))
    const ctx = mountComposable()
    ctx.api().warmUp()

    // ACT
    await typeBookmark(ctx)

    // ASSERT — an unknown setting must not be read as "on": a host that turned
    // auto-fire off has to see zero calls it did not ask for.
    expect(suggestSpy).not.toHaveBeenCalled()
  })

  it('distinguishes an unavailable model from an empty answer', async () => {
    // ARRANGE — BR-108-5, the defect that made a stalled model look like a
    // working one with no ideas.
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)
    await fillContent(ctx)

    // ACT / ASSERT
    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Empty))
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)
    expect(ctx.api().aiState.value).toBe('empty')

    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Unavailable))
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)
    expect(ctx.api().aiState.value).toBe('unavailable')

    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Preparing))
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)
    expect(ctx.api().aiState.value).toBe('preparing')
  })

  it('reports unavailable rather than empty when the request fails', async () => {
    // ARRANGE
    suggestSpy.mockRejectedValue(new Error('network down'))
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)
    await fillContent(ctx)

    // ACT
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)

    // ASSERT
    expect(ctx.api().aiState.value).toBe('unavailable')
    expect(ctx.api().aiSuggestions.value).toEqual([])
  })

  it('aborts a request that outlives the budget instead of spinning forever', async () => {
    // ARRANGE — BR-108-1: the client keeps its own clock so a wedged server
    // cannot leave the dialog's spinner running indefinitely.
    let signal: AbortSignal | undefined
    suggestSpy.mockImplementation((_req: unknown, init: { signal: AbortSignal }) => {
      signal = init.signal
      return new Promise(() => {})
    })
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)
    await fillContent(ctx)

    // ACT
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)
    expect(ctx.api().aiState.value).toBe('loading')
    expect(signal?.aborted).toBe(false)

    // ASSERT — the server budget is 8s, the client allows 2s of headroom.
    await vi.advanceTimersByTimeAsync(10_500)
    expect(signal?.aborted).toBe(true)
  })

  it('makes no model call at all when the collection has opted out', async () => {
    // ARRANGE — UC-112 BR-112-3/BR-112-4. The server refuses anyway; not asking
    // keeps a switched-off collection from generating request noise.
    warmUpSpy.mockResolvedValue(provider(true, 8000, false))
    suggestSpy.mockResolvedValue(result(SuggestionStatusJson.Ok, [{ id: 't-1' }]))
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)

    // ACT — typing, and then asking explicitly.
    await typeBookmark(ctx)
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)

    // ASSERT
    expect(suggestSpy).not.toHaveBeenCalled()
    expect(ctx.api().aiEnabled.value).toBe(false)
  })

  it('reports the feature as off until warm-up says otherwise', async () => {
    // ARRANGE — an affordance that appears and then vanishes is worse than one
    // that appears a beat late, so "not known yet" renders as off.
    warmUpSpy.mockReturnValue(new Promise(() => {}))
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)

    // ASSERT
    expect(ctx.api().aiEnabled.value).toBe(false)
  })

  it('aborts an in-flight request when the dialog closes', async () => {
    // ARRANGE — A6: the save must not wait on a suggestion still in the air.
    let signal: AbortSignal | undefined
    suggestSpy.mockImplementation((_req: unknown, init: { signal: AbortSignal }) => {
      signal = init.signal
      return new Promise(() => {})
    })
    const ctx = mountComposable()
    ctx.api().warmUp()
    await vi.advanceTimersByTimeAsync(0)
    await fillContent(ctx)
    ctx.api().retrieve()
    await vi.advanceTimersByTimeAsync(0)

    // ACT
    ctx.unmount()

    // ASSERT
    expect(signal?.aborted).toBe(true)
  })
})
