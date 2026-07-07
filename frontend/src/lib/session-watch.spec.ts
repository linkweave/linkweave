// @vitest-environment happy-dom
import { installSessionExpiryWatch } from './session-watch'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

let active = true
let expiredCalls = 0

// The watch installs a single document-level listener (install-once guard),
// so all tests share one installation and steer it via `active`/`fetchMock`.
installSessionExpiryWatch({
  isSessionAvailable: () => active,
  onExpired: () => {
    expiredCalls++
  },
})

async function fireVisibilityChange() {
  document.dispatchEvent(new Event('visibilitychange'))
  // let the probe's fetch promise chain settle
  await new Promise((resolve) => setTimeout(resolve, 0))
}

// Monotonic fake clock: the throttle state inside session-watch persists
// across tests, so each test must start well past the previous probe.
let clock = Date.now()

beforeEach(() => {
  active = true
  expiredCalls = 0
  fetchMock.mockReset()
  vi.useFakeTimers({ toFake: ['Date'] })
  clock += 120_000
  vi.setSystemTime(clock)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('session-watch', () => {
  it('should fire onExpired when the probe returns 499', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 499 }))
    await fireVisibilityChange()
    expect(expiredCalls).toBe(1)
  })

  it('should fire onExpired when the probe returns 401', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }))
    await fireVisibilityChange()
    expect(expiredCalls).toBe(1)
  })

  it('should not fire onExpired when the session is still valid', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
    await fireVisibilityChange()
    expect(expiredCalls).toBe(0)
  })

  it('should not treat a network failure as expiry (BR-098-1)', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await fireVisibilityChange()
    expect(expiredCalls).toBe(0)
  })

  it('should not treat a server error as expiry (BR-098-1)', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }))
    await fireVisibilityChange()
    expect(expiredCalls).toBe(0)
  })

  it('should not probe when no user is signed in', async () => {
    active = false
    await fireVisibilityChange()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('should throttle probes within the 60s window (BR-098-2)', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
    await fireVisibilityChange()
    await fireVisibilityChange()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    clock += 61_000
    vi.setSystemTime(clock)
    await fireVisibilityChange()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('should not probe when the tab is being hidden', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      configurable: true,
    })
    try {
      await fireVisibilityChange()
      expect(fetchMock).not.toHaveBeenCalled()
    } finally {
      Object.defineProperty(document, 'visibilityState', {
        value: 'visible',
        configurable: true,
      })
    }
  })
})
