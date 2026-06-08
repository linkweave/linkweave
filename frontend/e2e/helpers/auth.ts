import { expect, type Page } from '@playwright/test'
import { LoginPageObject } from '../models/LoginPageObject'

/** Logs in as the seeded dev user (alice) through the UI. */
export async function login(page: Page): Promise<void> {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login('alice@example.com', 'alice')
  await expect(page).toHaveURL(/\/collections\//, { timeout: 10000 })
}

/** Logs in as alice and navigates to the given collection. */
export async function loginAndNavigateToCollection(
  page: Page,
  collectionId: string,
): Promise<void> {
  await login(page)
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
}
