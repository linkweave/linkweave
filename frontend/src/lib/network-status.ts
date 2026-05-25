import { computed, ref } from 'vue'

export type OfflineReason = 'browser' | 'server' | null

const POLL_DELAYS_MS = [5_000, 10_000, 20_000, 30_000]
const PROBE_PATH = '/api/ping'

const browserOffline = ref(!navigator.onLine)
const serverUnreachable = ref(false)

export const offlineReason = computed<OfflineReason>(() => {
  if (browserOffline.value) return 'browser'
  if (serverUnreachable.value) return 'server'
  return null
})

export const isOffline = computed(() => offlineReason.value !== null)

let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollAttempt = 0
let onServerReachableAgain: (() => void) | null = null

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
  pollAttempt = 0
}

function schedulePoll() {
  const delay = POLL_DELAYS_MS[Math.min(pollAttempt, POLL_DELAYS_MS.length - 1)]!
  pollAttempt++
  pollTimer = setTimeout(probe, delay)
}

const UPSTREAM_DOWN_STATUSES = new Set([404, 502, 503, 504])

async function probe() {
  pollTimer = null
  try {
    const res = await fetch(PROBE_PATH, { credentials: 'omit', cache: 'no-store' })
    if (UPSTREAM_DOWN_STATUSES.has(res.status)) {
      schedulePoll()
      return
    }
    markServerReachable()
  } catch {
    schedulePoll()
  }
}

export function markServerUnreachable() {
  if (serverUnreachable.value) return
  serverUnreachable.value = true
  pollAttempt = 0
  schedulePoll()
}

export function markServerReachable() {
  const wasUnreachable = serverUnreachable.value
  serverUnreachable.value = false
  stopPolling()
  if (wasUnreachable) onServerReachableAgain?.()
}

export function setBrowserOffline(value: boolean) {
  browserOffline.value = value
}

let listenersInstalled = false
export function installNetworkStatusListeners(opts: { onServerBack?: () => void } = {}) {
  if (opts.onServerBack) onServerReachableAgain = opts.onServerBack
  if (listenersInstalled) return
  listenersInstalled = true

  const probeIfUnreachable = () => {
    if (!serverUnreachable.value) return
    stopPolling()
    pollAttempt = 0
    probe()
  }

  window.addEventListener('focus', probeIfUnreachable)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') probeIfUnreachable()
  })
}
