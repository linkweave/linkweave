import { expect, type Page } from '@playwright/test'

const TOAST_SELECTOR = '[data-sonner-toast]'

declare global {
  interface Window {
    __seenToasts?: string[]
  }
}

/**
 * Starts recording every toast that appears, so a test can assert on one that
 * has since auto-dismissed — or, more importantly, that a toast never appeared
 * at all.
 *
 * Asserting `toHaveCount(0)` against the live DOM cannot do the second job: a
 * toast that shows and expires before the assertion runs looks exactly like a
 * toast that was never shown. That is not hypothetical — it is how the BR-205
 * test in `live-updates.spec.ts` first passed against a server with the
 * self-filtering disabled.
 *
 * Call before the action under test; the recorder survives until navigation.
 */
export async function recordToasts(page: Page): Promise<void> {
  await page.evaluate((selector) => {
    window.__seenToasts = []
    const capture = () => {
      document.querySelectorAll(selector).forEach((el) => {
        const text = el.textContent?.trim()
        if (text && !window.__seenToasts!.includes(text)) window.__seenToasts!.push(text)
      })
    }
    capture()
    new MutationObserver(capture).observe(document.body, { childList: true, subtree: true })
  }, TOAST_SELECTOR)
}

/** Every distinct toast seen since {@link recordToasts}, dismissed or not. */
export async function seenToasts(page: Page): Promise<string[]> {
  return page.evaluate(() => window.__seenToasts ?? [])
}

/** Waits until a toast containing `text` has been seen at some point. */
export async function expectToastSeen(page: Page, text: string): Promise<void> {
  await expect
    .poll(async () => (await seenToasts(page)).some((t) => t.includes(text)), {
      message: `expected a toast containing "${text}"`,
      timeout: 15_000,
    })
    .toBe(true)
}
