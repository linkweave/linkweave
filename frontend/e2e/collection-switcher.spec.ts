import { expect, test } from './fixtures'
import { createCollectionViaApi } from './helpers/api'
import { CollectionSwitcherPageObject } from './models/CollectionSwitcherPageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

let user: TestUser
let storageState: StorageState

test.describe('Collection Switcher', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'colswitcher'))
    // Names of clearly different lengths next to the auto-provisioned default
    // collection, so any centring of the names shows up as differing left edges.
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      await createCollectionViaApi(ctx.request, 'A')
      await createCollectionViaApi(ctx.request, 'Somewhat longer name')
    } finally {
      await ctx.close()
    }
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/collections\//, { timeout: 15000 })
  })

  test('should show switcher button in sidebar', async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)
    await expect(switcher.getTriggerButton()).toBeVisible()
  })

  test('should open and close dropdown', async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)

    await switcher.openSwitcher()
    await switcher.expectDropdownVisible()

    await switcher.closeByClickingOutside()
    await switcher.expectDropdownHidden()
  })

  test('should show default badge next to default collection', async ({ page }) => {
    // ARRANGE
    const switcher = new CollectionSwitcherPageObject(page)

    // ACT
    await switcher.openSwitcher()
    // ASSERT
    const badges = page.locator('[data-testid^="collection-default-badge-"]')
    await expect(badges).toHaveCount(1)
  })

  test('should left-align collection names regardless of name length or default badge', async ({ page }) => {
    // ARRANGE
    const switcher = new CollectionSwitcherPageObject(page)

    // ACT
    await switcher.openSwitcher()

    // ASSERT
    await expect(page.getByTestId(/^collection-name-/)).toHaveCount(3)
    const leftEdges = await switcher.collectionNameTextLeftEdges()
    expect(Math.max(...leftEdges) - Math.min(...leftEdges)).toBeLessThan(1)
  })

  test('should show "This is the default" when on default collection', async ({ page }) => {
    // ARRANGE
    const switcher = new CollectionSwitcherPageObject(page)

    // ACT
    await switcher.openSwitcher()
    // ASSERT
    await switcher.expectShowsThisIsDefault()
    await switcher.expectSetAsDefaultDisabled()
  })

  test('should close dropdown when pressing Escape', async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)

    await switcher.openSwitcher()
    await switcher.expectDropdownVisible()

    await page.keyboard.press('Escape')
    await switcher.expectDropdownHidden()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
