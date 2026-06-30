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
  if (tagNames.length > 0) {
    // Tags are selected through the combobox: open it, toggle each existing
    // tag's checklist row, then close the popover (toggle the trigger) and wait
    // for it to disappear before submitting.
    const trigger = dialog.getByTestId('create-bookmark-tags-trigger')
    await trigger.click()
    for (const tagName of tagNames) {
      await page.getByTestId(`tags-row-${tagName}`).click()
    }
    await trigger.click()
    await expect(page.getByTestId('create-bookmark-tags-popover')).toBeHidden()
  }
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).not.toBeVisible()
}
