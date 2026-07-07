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

const SPOT_CHECK_THROTTLE_MS = 30_000
let lastSpotCheckAt = 0

/**
 * Detects the server going down while the app is idle: reachability is
 * otherwise only discovered passively from failing requests, so a server
 * that dies while no traffic flows stays undetected until the next user
 * action. Deliberately separate from `probe()` — that one is the recovery
 * loop and must keep its backoff semantics. A network failure only counts
 * when the browser itself is online; browser-offline has its own detection.
 */
async function spotCheck() {
  const now = Date.now()
  if (now - lastSpotCheckAt < SPOT_CHECK_THROTTLE_MS) return
  lastSpotCheckAt = now
  try {
    const res = await fetch(PROBE_PATH, { credentials: 'omit', cache: 'no-store' })
    if (UPSTREAM_DOWN_STATUSES.has(res.status)) markServerUnreachable()
  } catch {
    if (navigator.onLine) markServerUnreachable()
  }
}

let listenersInstalled = false
export function installNetworkStatusListeners(opts: { onServerBack?: () => void } = {}) {
  if (opts.onServerBack) onServerReachableAgain = opts.onServerBack
  if (listenersInstalled) return
  listenersInstalled = true

  const probeOnFocus = () => {
    if (serverUnreachable.value) {
      // already known-down: restart the recovery poller immediately, unthrottled
      stopPolling()
      pollAttempt = 0
      probe()
      return
    }
    void spotCheck()
  }

  window.addEventListener('focus', probeOnFocus)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') probeOnFocus()
  })
}
