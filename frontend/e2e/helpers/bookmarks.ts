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
