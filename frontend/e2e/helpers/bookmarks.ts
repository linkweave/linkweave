import { expect, type Locator, type Page } from '@playwright/test'

/**
 * Navigates to the collection, opens the "Add bookmark" dialog, and returns it
 * once visible. The page must already be authenticated (specs provide a
 * per-spec user via the `storageState` fixture).
 */
export async function openAddBookmarkDialog(page: Page, collectionId: string): Promise<Locator> {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
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
      // Under parallel e2e load the tag-list GET inside the popover can lag
      // the popover open by tens of seconds. Wait explicitly with a generous
      // budget so a freshly-created tag is reliably picked up.
      const tagRow = page.getByTestId(`tags-row-${tagName}`)
      try {
        await expect(tagRow).toBeVisible({ timeout: 15000 })
      } catch {
        // Re-open the popover to force a fresh fetch of the tag list.
        await trigger.click()
        await expect(page.getByTestId('create-bookmark-tags-popover')).toBeHidden({
          timeout: 10000,
        })
        await trigger.click()
        await expect(tagRow).toBeVisible({ timeout: 30000 })
      }
      await tagRow.click()
    }
    await trigger.click()
    await expect(page.getByTestId('create-bookmark-tags-popover')).toBeHidden({ timeout: 10000 })
  }
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).not.toBeVisible({ timeout: 30000 })
}
