import { expect, test } from '@playwright/test'
import { CollectionSwitcherPageObject } from './models/CollectionSwitcherPageObject'
import { deleteTestUserCleanup, registerTestUser, type TestUser } from './models/TestUser'

let user: TestUser

test.describe('Collection Switcher', () => {
  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      user = await registerTestUser(ctx.request, 'colswitcher')
    } finally {
      await ctx.close()
    }
  })

  test.beforeEach(async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)
    await switcher.loginAndWaitForPage(user.email, user.password)
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
    const switcher = new CollectionSwitcherPageObject(page)

    await switcher.openSwitcher()
    const badges = page.locator('[data-testid^="collection-default-badge-"]')
    await expect(badges).toHaveCount(1)
  })

  test('should show "This is the default" when on default collection', async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)

    await switcher.openSwitcher()
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
