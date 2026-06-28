<script setup lang="ts">
import { computed, ref } from 'vue'
import * as Sentry from '@sentry/vue'
import ButtonLw from '@/components/ui/ButtonLw.vue'

interface DsnInfo {
  valid: boolean
  raw: string
  publicKey?: string
  host?: string
  projectId?: string
}

const dsnEnv = (import.meta.env.VITE_SENTRY_DSN as string | undefined) ?? ''
const mode = import.meta.env.MODE
const isDev = import.meta.env.DEV

const dsnInfo = computed<DsnInfo>(() => {
  if (!dsnEnv) return { valid: false, raw: '' }
  try {
    const url = new URL(dsnEnv)
    return {
      valid: true,
      raw: dsnEnv,
      publicKey: url.username || undefined,
      host: url.host,
      projectId: url.pathname.replace(/^\//, ''),
    }
  } catch {
    return { valid: false, raw: dsnEnv }
  }
})

const client = Sentry.getClient()
const options = client?.getOptions?.()
const sdkEnabled = options?.enabled ?? false
const tunnel = options?.tunnel ?? '(none)'
const environment = options?.environment ?? mode
const tracesSampleRate = options?.tracesSampleRate

interface LogEntry {
  time: string
  level: 'success' | 'error'
  message: string
}
const logEntries = ref<LogEntry[]>([])
const busy = ref<'message' | 'exception' | null>(null)

function pushLog(level: LogEntry['level'], message: string) {
  logEntries.value.unshift({
    time: new Date().toLocaleTimeString(),
    level,
    message,
  })
}

async function sendTestMessage() {
  busy.value = 'message'
  const marker = `dev-sentry-test-${Date.now()}`
  try {
    Sentry.captureMessage(`Sentry DSN test (${marker})`, {
      level: 'info',
      tags: { source: 'dev-sentry-test' },
    })
    const flushed = await Sentry.flush(2000)
    pushLog(flushed ? 'success' : 'error', `captureMessage sent (${marker}). flush=${flushed}`)
  } catch (e) {
    pushLog('error', `captureMessage failed: ${(e as Error).message}`)
  } finally {
    busy.value = null
  }
}

async function sendTestException() {
  busy.value = 'exception'
  const marker = `dev-sentry-test-${Date.now()}`
  try {
    Sentry.captureException(
      new Error(`Intentional test error from /dev/sentry-test (${marker})`),
      { tags: { source: 'dev-sentry-test' } },
    )
    const flushed = await Sentry.flush(2000)
    pushLog(flushed ? 'success' : 'error', `captureException sent (${marker}). flush=${flushed}`)
  } catch (e) {
    pushLog('error', `captureException failed: ${(e as Error).message}`)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="min-h-screen bg-background text-foreground px-4 py-10">
    <div class="max-w-2xl mx-auto space-y-6">
      <header class="space-y-1">
        <div class="flex items-center gap-2">
          <h1 class="text-2xl font-bold tracking-tight">Sentry Diagnostics</h1>
          <span class="rounded bg-amber-500/15 text-amber-600 text-xs font-medium px-2 py-0.5">dev</span>
        </div>
        <p class="text-sm text-muted-foreground">
          Hidden diagnostic page to verify the Sentry DSN was injected at build time and that events
          reach your Sentry project. Not linked in the UI — access via <code>/dev/sentry-test</code>.
        </p>
      </header>

      <section class="rounded-lg border border-input p-4 space-y-3">
        <h2 class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Build-time DSN</h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-muted-foreground">VITE_SENTRY_DSN set</dt>
          <dd class="font-medium" :class="dsnEnv ? 'text-green-600' : 'text-red-600'">
            {{ dsnEnv ? 'yes' : 'no (empty)' }}
          </dd>
          <dt class="text-muted-foreground">Valid URL</dt>
          <dd class="font-medium" :class="dsnInfo.valid ? 'text-green-600' : 'text-red-600'">
            {{ dsnInfo.valid ? 'yes' : 'no' }}
          </dd>
          <template v-if="dsnInfo.valid">
            <dt class="text-muted-foreground">Host</dt>
            <dd class="font-mono">{{ dsnInfo.host }}</dd>
            <dt class="text-muted-foreground">Project ID</dt>
            <dd class="font-mono">{{ dsnInfo.projectId }}</dd>
            <dt class="text-muted-foreground">Public key</dt>
            <dd class="font-mono">
              {{ dsnInfo.publicKey ? dsnInfo.publicKey.slice(0, 8) + '…' : '(missing)' }}
            </dd>
          </template>
        </dl>
        <details class="text-xs">
          <summary class="cursor-pointer text-muted-foreground">Show raw DSN value</summary>
          <code class="block mt-2 break-all rounded bg-muted px-2 py-1">{{ dsnInfo.raw || '(empty)' }}</code>
        </details>
      </section>

      <section class="rounded-lg border border-input p-4 space-y-3">
        <h2 class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">SDK runtime</h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
          <dt class="text-muted-foreground">Client initialized</dt>
          <dd class="font-medium" :class="client ? 'text-green-600' : 'text-red-600'">
            {{ client ? 'yes' : 'no' }}
          </dd>
          <dt class="text-muted-foreground">SDK enabled</dt>
          <dd class="font-medium" :class="sdkEnabled ? 'text-green-600' : 'text-red-600'">
            {{ sdkEnabled ? 'yes' : 'no' }}
          </dd>
          <dt class="text-muted-foreground">Tunnel</dt>
          <dd class="font-mono">{{ tunnel }}</dd>
          <dt class="text-muted-foreground">Environment</dt>
          <dd class="font-mono">{{ environment }}</dd>
          <dt class="text-muted-foreground">Build mode</dt>
          <dd class="font-mono">{{ isDev ? 'DEV' : 'production' }} ({{ mode }})</dd>
          <dt class="text-muted-foreground">tracesSampleRate</dt>
          <dd class="font-mono">{{ tracesSampleRate ?? '—' }}</dd>
        </dl>
        <p v-if="!dsnEnv" class="text-xs text-amber-600">
          The DSN is empty — Sentry will no-op. If you expected it set, check that the build received
          <code>VITE_SENTRY_DSN</code> (Docker build-arg / env var).
        </p>
      </section>

      <section class="rounded-lg border border-input p-4 space-y-3">
        <h2 class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Send test events</h2>
        <div class="flex flex-wrap gap-2">
          <ButtonLw variant="default" :disabled="busy !== null" @click="sendTestMessage">
            {{ busy === 'message' ? 'Sending…' : 'Send test message' }}
          </ButtonLw>
          <ButtonLw variant="destructive" :disabled="busy !== null" @click="sendTestException">
            {{ busy === 'exception' ? 'Sending…' : 'Capture test exception' }}
          </ButtonLw>
        </div>
        <p class="text-xs text-muted-foreground">
          Events are tagged <code>source: dev-sentry-test</code> so they're easy to find in your Sentry
          dashboard. <code>flush(2000)</code> forces immediate delivery through the tunnel.
        </p>
        <div v-if="logEntries.length" class="mt-2 space-y-1">
          <div
            v-for="(entry, i) in logEntries"
            :key="i"
            class="flex items-start gap-2 text-xs font-mono"
          >
            <span class="text-muted-foreground">{{ entry.time }}</span>
            <span
              :class="entry.level === 'success' ? 'text-green-600' : 'text-red-600'"
            >{{ entry.message }}</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
