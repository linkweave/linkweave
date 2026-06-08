import { expect, type Locator, type Page } from '@playwright/test'
import { loginAndNavigateToCollection } from './auth'

/**
 * Logs in, navigates to the collection, opens the "Add bookmark" dialog, and
 * returns it once visible.
 */
export async function openAddBookmarkDialog(page: Page, collectionId: string): Promise<Locator> {
  await loginAndNavigateToCollection(page, collectionId)
  await page.getByRole('button', { name: /add bookmark/i }).click()
  const dialog = page.locator('[role="dialog"]')
  await expect(dialog).toBeVisible()
  return dialog
}

/**
 * Creates a bookmark through the UI on the current collection page: opens the
 * "Add bookmark" dialog, fills title/url, selects any given tags, submits, and
 * waits for the dialog to close. Assumes the collection is already open.
 */
export async function createBookmarkViaUi(
  page: Page,
  title: string,
  url: string,
  tagNames: string[] = [],
): Promise<void> {
  await page.getByRole('button', { name: /add bookmark/i }).click()
  const dialog = page.locator('[role="dialog"]')
  await expect(dialog).toBeVisible()
  await dialog.locator('#create-bookmark-title').fill(title)
  await dialog.locator('#create-bookmark-url').fill(url)
  for (const tagName of tagNames) {
    await dialog.locator('button').filter({ hasText: tagName }).click()
  }
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).not.toBeVisible()
}
