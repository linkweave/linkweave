// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { createApp, h, nextTick, type App } from 'vue'
import type { BookmarkJson } from '@/api/generated'

// Mock the host-permission layer and the config/client layer so the store can
// be used without chrome.* or a real backend (same pattern as extension.spec.ts).
vi.mock('../api/permissions', () => ({
  hasApiPermission: vi.fn(),
  requestApiPermission: vi.fn(),
}))
vi.mock('../api/client', () => ({
  loadExtensionConfig: vi.fn(),
  createApiConfig: vi.fn(() => ({})),
}))

import SaveView from './SaveView.vue'
import { useExtensionStore } from '../stores/extension'

let app: App | null = null
let root: HTMLElement | null = null
let pinia: Pinia

function mountView(initialUrl = '') {
  pinia = createPinia()
  setActivePinia(pinia)
  const store = useExtensionStore()
  store.currentCollectionId = 'col-1'
  // Replace the action so no real API client is needed; the URL guard must
  // stop invalid values BEFORE this is ever called.
  store.createBookmark = vi.fn().mockResolvedValue({ id: 'bm-1' } as BookmarkJson)

  app = createApp({
    render: () => h(SaveView, { initialUrl, initialTitle: 'Some title' }),
  })
  app.use(pinia)
  root = document.createElement('div')
  document.body.appendChild(root)
  app.mount(root)
  return store
}

async function fillUrl(value: string) {
  const input = document.querySelector('input[type="url"]') as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  // The real flow prefixes a bare host with https:// on blur.
  input.dispatchEvent(new Event('blur'))
  await nextTick()
}

async function submitForm() {
  const form = document.querySelector('form') as HTMLFormElement
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await nextTick()
}

function visibleError(): string | null {
  const p = [...document.querySelectorAll('p')].find((el) =>
    el.className.includes('text-destructive'),
  )
  return p?.textContent ?? null
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  app?.unmount()
  root?.remove()
  app = null
  root = null
})

describe('SaveView — url validation before create', () => {
  it('rejects a url with spaces in the authority and never calls the API', async () => {
    // ARRANGE
    const store = mountView()
    await fillUrl('eSAD - Application BS - PROD')

    // ACT
    await submitForm()

    // ASSERT
    expect(visibleError()).toBe('Please enter a valid URL.')
    expect(store.createBookmark).not.toHaveBeenCalled()
  })

  it('saves a valid url', async () => {
    // ARRANGE
    const store = mountView()
    await fillUrl('https://example.com/page')

    // ACT
    await submitForm()

    // ASSERT
    expect(visibleError()).toBeNull()
    expect(store.createBookmark).toHaveBeenCalledTimes(1)
  })
})
