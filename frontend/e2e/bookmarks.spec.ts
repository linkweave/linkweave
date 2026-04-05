import { expect, test } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'

async function loginAsAlice(page: import('@playwright/test').Page) {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login()
  await expect(page).toHaveURL(/\/collections\//)
}

test.describe('UC-001: Auto-Provision on First Login', () => {
  test('should create default collection and redirect to it', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await loginPage.login()
    await expect(page).toHaveURL(/\/collections\//)
  })
})

test.describe('UC-002: Auto-Navigate to Collection', () => {
  test('should redirect to collection after login', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await loginPage.login()
    await expect(page).toHaveURL(/\/collections\//)
  })
})

test.describe('UC-005: Create Bookmark', () => {
  test('should create a bookmark', async ({ page }) => {
    await loginAsAlice(page)

    const title = `Test-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://example.com/create')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-006: View Bookmarks', () => {
  test('should view bookmarks in the list', async ({ page }) => {
    await loginAsAlice(page)
    await expect(page.locator('[data-testid^="bookmark-card-"]').first()).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-007: Edit Bookmark', () => {
  test('should edit a bookmark', async ({ page }) => {
    await loginAsAlice(page)

    const title = `Edit-${Date.now()}`
    const newTitle = `${title}-v2`

    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://before.com')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })

    const card = page.getByTestId(`bookmark-card-${title}`)
    await card.hover()
    await card.locator('button').last().click({ force: true })
    await page.getByTestId('bookmark-edit-btn').click()

    await page.getByTestId('edit-bookmark-title').clear()
    await page.getByTestId('edit-bookmark-title').fill(newTitle)
    await page.getByTestId('edit-bookmark-url').clear()
    await page.getByTestId('edit-bookmark-url').fill('https://after.com')
    await page.getByTestId('edit-bookmark-submit').click()

    await expect(page.getByTestId(`bookmark-card-${newTitle}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-008: Delete Bookmark', () => {
  test('should delete a bookmark', async ({ page }) => {
    await loginAsAlice(page)

    const title = `Delete-${Date.now()}`

    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://delete.com')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })

    const card = page.getByTestId(`bookmark-card-${title}`)
    await card.hover()
    await card.locator('button').last().click({ force: true })
    await page.getByTestId('bookmark-delete-btn').click()
    await page.getByTestId('confirm-dialog-submit').click()

    await expect(page.getByTestId(`bookmark-card-${title}`)).not.toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-013: Move Bookmark to Folder', () => {
  test('should move bookmark into a folder', async ({ page }) => {
    await loginAsAlice(page)

    const folderName = `move-folder-${Date.now()}`
    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(folderName)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${folderName}`)).toBeVisible({ timeout: 5000 })

    const title = `Move-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://move.com')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })

    const card = page.getByTestId(`bookmark-card-${title}`)
    await card.hover()
    await card.locator('button').last().click({ force: true })
    await page.getByTestId('bookmark-move-btn').click()

    await page.getByTestId('move-bookmark-folder').selectOption({ label: folderName })
    await page.getByTestId('move-bookmark-submit').click()

    await page.getByTestId(`folder-node-${folderName}`).click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-019: Apply Tag to Bookmark', () => {
  test('should apply tag when creating bookmark', async ({ page }) => {
    await loginAsAlice(page)

    const tagName = `apply-tag-${Date.now()}`
    await page.getByTestId('new-tag-btn').click()
    await page.getByTestId('create-tag-name-input').fill(tagName)
    await page.getByTestId('create-tag-submit').click()
    await expect(page.getByTestId(`tag-row-${tagName}`)).toBeVisible({ timeout: 5000 })

    const title = `Tagged-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://tagged.com')
    const dialog = page.locator('[role="dialog"][data-state="open"]').last()
    await dialog.locator('button[type="button"]').filter({ hasText: new RegExp(`^${tagName}$`) }).first().click()
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-020: Remove Tag from Bookmark', () => {
  test('should remove tag from bookmark via edit', async ({ page }) => {
    await loginAsAlice(page)

    const tagName = `rem-tag-${Date.now()}`
    await page.getByTestId('new-tag-btn').click()
    await page.getByTestId('create-tag-name-input').fill(tagName)
    await page.getByTestId('create-tag-submit').click()
    await expect(page.getByTestId(`tag-row-${tagName}`)).toBeVisible({ timeout: 5000 })

    const title = `Untag-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://untag.com')
    const createOverlay = page.locator('[role="dialog"][data-state="open"]').last()
    await createOverlay.locator('button').filter({ hasText: new RegExp(`^${tagName}$`) }).first().click()
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })

    const card = page.getByTestId(`bookmark-card-${title}`)
    await card.hover()
    await card.locator('button').last().click({ force: true })
    await page.getByTestId('bookmark-edit-btn').click()

    const editOverlay = page.locator('[role="dialog"][data-state="open"]').last()
    await editOverlay.locator('button').filter({ hasText: new RegExp(`^${tagName}$`) }).first().click()
    await page.getByTestId('edit-bookmark-submit').click()

    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })
    const updatedCard = page.getByTestId(`bookmark-card-${title}`)
    await expect(updatedCard.getByText(tagName)).not.toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-021: Filter Bookmarks by Tag', () => {
  test('should filter bookmarks by selecting a tag', async ({ page }) => {
    await loginAsAlice(page)

    const tagName = `filter-tag-${Date.now()}`
    await page.getByTestId('new-tag-btn').click()
    await page.getByTestId('create-tag-name-input').fill(tagName)
    await page.getByTestId('create-tag-submit').click()
    await expect(page.getByTestId(`tag-row-${tagName}`)).toBeVisible({ timeout: 5000 })

    const taggedTitle = `Filtered-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(taggedTitle)
    await page.getByTestId('create-bookmark-url').fill('https://filtered.com')
    const overlay1 = page.locator('[role="dialog"][data-state="open"]').last()
    await overlay1.locator('button[type="button"]').filter({ hasText: new RegExp(`^${tagName}$`) }).first().click()
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${taggedTitle}`)).toBeVisible({ timeout: 10000 })

    const untaggedTitle = `Unfiltered-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(untaggedTitle)
    await page.getByTestId('create-bookmark-url').fill('https://unfiltered.com')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${untaggedTitle}`)).toBeVisible({ timeout: 10000 })

    await page.getByTestId(`tag-row-${tagName}`).click()

    await expect(page.getByTestId(`bookmark-card-${taggedTitle}`)).toBeVisible({ timeout: 5000 })
    await expect(page.getByTestId(`bookmark-card-${untaggedTitle}`)).not.toBeVisible()

    await page.getByText(/clear filter/i).click()
    await expect(page.getByTestId(`bookmark-card-${untaggedTitle}`)).toBeVisible({ timeout: 5000 })
  })
})

test.describe('UC-032: Search Bookmarks', () => {
  test('should search bookmarks by title', async ({ page }) => {
    await loginAsAlice(page)

    const title = `Search-${Date.now()}`
    await page.getByTestId('add-bookmark-btn').click()
    await page.getByTestId('create-bookmark-title').fill(title)
    await page.getByTestId('create-bookmark-url').fill('https://search.com')
    await page.getByTestId('create-bookmark-submit').click()
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })

    await page.getByTestId('search-input').fill(title)
    await expect(page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 5000 })

    await page.getByTestId('search-input').fill('zzz-no-match-xyz')
    await expect(page.getByTestId(`bookmark-card-${title}`)).not.toBeVisible()
  })
})
