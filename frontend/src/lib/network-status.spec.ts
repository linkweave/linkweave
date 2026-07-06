// @vitest-environment happy-dom
import {
  installNetworkStatusListeners,
  markServerReachable,
  offlineReason,
} from './network-status'

const fetchMock = vi.fn<typeof fetch>()
vi.stubGlobal('fetch', fetchMock)

// Single document/window-level installation (install-once guard); tests
// steer it via fetchMock and reset reachability in beforeEach.
installNetworkStatusListeners()

async function fireFocus() {
  window.dispatchEvent(new Event('focus'))
  // let the spot check's fetch promise chain settle
  await new Promise((resolve) => setTimeout(resolve, 0))
}

// Monotonic fake clock: the spot-check throttle persists across tests, so
// each test must start well past the previous probe.
let clock = Date.now()

beforeEach(() => {
  fetchMock.mockReset()
  markServerReachable() // reset state, stop any recovery poller
  vi.useFakeTimers({ toFake: ['Date'] })
  clock += 120_000
  vi.setSystemTime(clock)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('network-status focus spot check', () => {
  it('should flip to server-offline when the focus probe hits a gateway error', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 502 }))
    await fireFocus()
    expect(offlineReason.value).toBe('server')
  })

  it('should stay online when the probe answers successfully', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await fireFocus()
    expect(offlineReason.value).toBeNull()
  })

  it('should treat a network failure while the browser is online as server-offline', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await fireFocus()
    expect(offlineReason.value).toBe('server')
  })

  it('should not flip to server-offline when the browser itself is offline', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    Object.defineProperty(window.navigator, 'onLine', { value: false, configurable: true })
    try {
      await fireFocus()
      expect(offlineReason.value).toBeNull()
    } finally {
      Object.defineProperty(window.navigator, 'onLine', { value: true, configurable: true })
    }
  })

  it('should throttle healthy spot checks within the 30s window', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await fireFocus()
    await fireFocus()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    clock += 31_000
    vi.setSystemTime(clock)
    await fireFocus()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('should probe unthrottled and recover when focused while server-offline', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 502 }))
    await fireFocus()
    expect(offlineReason.value).toBe('server')

    // immediately after (well inside the throttle window): recovery path
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await fireFocus()
    expect(offlineReason.value).toBeNull()
  })
})
