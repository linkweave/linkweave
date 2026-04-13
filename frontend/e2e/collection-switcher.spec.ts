import { expect, test } from '@playwright/test'
import { CollectionSwitcherPageObject } from './models/CollectionSwitcherPageObject'

test.describe('Collection Switcher', () => {
  test.beforeEach(async ({ page }) => {
    const switcher = new CollectionSwitcherPageObject(page)
    await switcher.loginAndWaitForPage()
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
})
