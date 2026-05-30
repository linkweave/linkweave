import http from 'node:http'
import { chromium } from 'playwright'

const PORT = Number(process.env.PORT ?? 3000)
// Wait for window.load (images + sub-resources fetched), then a best-effort
// networkidle settle for late-stage JS hydration / web-font swap, then a
// short fixed pause so CSS transitions and async-decoded images get a frame
// or two to paint. networkidle is a network signal, not a paint signal — the
// fixed delay closes that gap. The Java caller uses a 15s HTTP read-timeout
// so the sidecar always wins the race and returns a clean error rather than
// getting cut off mid-response.
const NAV_TIMEOUT_MS = Number(process.env.NAV_TIMEOUT_MS ?? 10_000)
const SETTLE_TIMEOUT_MS = Number(process.env.SETTLE_TIMEOUT_MS ?? 2_500)
const POST_LOAD_DELAY_MS = Number(process.env.POST_LOAD_DELAY_MS ?? 300)
const MAX_BODY_BYTES = 8 * 1024

const ALLOWED_FORMATS = new Set(['jpeg', 'png'])

const browser = await chromium.launch({
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
})

process.on('SIGTERM', () => shutdown('SIGTERM'))
process.on('SIGINT', () => shutdown('SIGINT'))

async function shutdown(signal) {
  console.log(`received ${signal}, shutting down`)
  try {
    await browser.close()
  } finally {
    process.exit(0)
  }
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
  })
  res.end(payload)
}

async function readJsonBody(req) {
  let size = 0
  const chunks = []
  for await (const chunk of req) {
    size += chunk.length
    if (size > MAX_BODY_BYTES) throw new Error('body too large')
    chunks.push(chunk)
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf-8'))
}

function validateRequest(body) {
  if (!body || typeof body !== 'object') throw new Error('body must be an object')
  const { url, width = 1280, height = 800, format = 'jpeg', quality = 60 } = body

  if (typeof url !== 'string') throw new Error('url must be a string')
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    throw new Error('url is not a valid URL')
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('url must be http or https')
  }
  if (!ALLOWED_FORMATS.has(format)) throw new Error(`format must be one of ${[...ALLOWED_FORMATS].join(', ')}`)
  if (!Number.isInteger(width) || width < 320 || width > 3840) throw new Error('width out of range')
  if (!Number.isInteger(height) || height < 240 || height > 2160) throw new Error('height out of range')
  if (format === 'jpeg' && (!Number.isInteger(quality) || quality < 1 || quality > 100)) {
    throw new Error('quality out of range')
  }

  return { url: parsed.toString(), width, height, format, quality }
}

async function capture({ url, width, height, format, quality }) {
  const context = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: 1,
    userAgent:
      'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/Chainlink-Screenshot Safari/537.36',
  })
  try {
    const page = await context.newPage()
    await page.goto(url, { waitUntil: 'load', timeout: NAV_TIMEOUT_MS })
    // Best-effort: many real sites never hit networkidle (chat widgets,
    // analytics beacons, websockets). Catching the rejection lets us
    // capture *something* rather than time out on those.
    await page.waitForLoadState('networkidle', { timeout: SETTLE_TIMEOUT_MS }).catch(() => {})
    // Final paint settle — gives CSS transitions, async-decoded images, and
    // late hydration re-renders a frame or two to land before we snapshot.
    await page.waitForTimeout(POST_LOAD_DELAY_MS)
    const screenshotOptions = { type: format, fullPage: false }
    if (format === 'jpeg') screenshotOptions.quality = quality
    return await page.screenshot(screenshotOptions)
  } finally {
    await context.close()
  }
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    const connected = browser.isConnected()
    return sendJson(res, connected ? 200 : 503, { status: connected ? 'ok' : 'browser-disconnected' })
  }

  if (req.method === 'POST' && req.url === '/screenshot') {
    let params
    try {
      const body = await readJsonBody(req)
      params = validateRequest(body)
    } catch (err) {
      return sendJson(res, 400, { error: err.message })
    }

    try {
      const bytes = await capture(params)
      res.writeHead(200, {
        'content-type': params.format === 'png' ? 'image/png' : 'image/jpeg',
        'content-length': bytes.length,
      })
      return res.end(bytes)
    } catch (err) {
      console.warn(`capture failed for ${params.url}: ${err.message}`)
      return sendJson(res, 502, { error: 'capture failed', detail: err.message })
    }
  }

  sendJson(res, 404, { error: 'not found' })
})

server.listen(PORT, () => {
  console.log(`screenshot-service listening on :${PORT}`)
})
