import { expect, test } from './fixtures'
import {
  useTestCollectionWithCleanup,
  gotoCollection,
} from './helpers/testCollection'
import { api, createBookmarkViaApi } from './helpers/api'
import { loginViaApi } from './models/TestUser'

// UC-093: in list view with previews on, the hover preview popup floats over
// the row's right edge and used to hide the row's ⋯ menu. The popup now hosts
// the actions itself in a footer, so editing/deleting a bookmark whose preview
// is open stays reachable. These specs guard that the footer actions are
// visible, clickable, and wired to the same handlers as the row menu.

const collection = useTestCollectionWithCleanup('preview-popup-actions')

test.describe('UC-093 preview popup action footer', () => {
  test.use({
    storageState: async ({}, use) => {
      await use(collection.storageState!)
    },
  })

  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      await loginViaApi(ctx.request, collection.user!)
      const collectionId = collection.collectionId
      // The popup only renders when the collection has screenshots enabled.
      const info = await api<{ name: string }>(ctx.request, 'GET', `/api/collections/${collectionId}`)
      await api(ctx.request, 'PUT', `/api/collections/${collectionId}`, {
        name: info.name,
        screenshotEnabled: true,
      })
      await createBookmarkViaApi(
        ctx.request,
        collectionId,
        'UC-093 target',
        'https://example.com/uc-093-target',
      )
    } finally {
      await ctx.close()
    }
  })

  test('footer menu is reachable and opens Edit while popup is shown', async ({ page }) => {
    // Boot straight into list layout with previews on so the first row is
    // hover-zoomable without touching the toolbar.
    await page.addInitScript(() => {
      localStorage.setItem('bookmarkLayout', 'list')
      localStorage.setItem('previewsEnabled', 'true')
      localStorage.setItem('linkweave:showPreviewPopup', 'true')
    })
    await gotoCollection(page, collection)
    await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible({ timeout: 10000 })

    // Hover the row; the shared hover-intent controller shows the popup after
    // its cold-start dwell (DWELL_MS = 450ms).
    const card = page.getByTestId(/^bookmark-card-/).first()
    await card.hover()
    const popup = page.getByTestId('bookmark-preview-popup')
    await expect(popup).toBeVisible({ timeout: 5000 })

    // The capture area extends past the row and is the popup's largest surface.
    // Moving across it toward the footer must NOT dismiss the popup (regression
    // guard: the grace window alone was too tight to reach the footer). Move by
    // raw coordinate to a point past the row's bottom edge, wait past the hide
    // grace, and assert the popup is still open.
    const box = await popup.boundingBox()
    expect(box).not.toBeNull()
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height - 60)
    await page.waitForTimeout(200)
    await expect(popup).toBeVisible()

    // The popup is clamped to never overlap the sticky toolbar, so a toolbar
    // control stays clickable even while the popup is up (UC-093: the popup
    // must not block other consumers). The settings gear lives in the toolbar.
    const settingsBtn = page.getByTestId('collection-settings-open')
    await expect(settingsBtn).toBeVisible()
    await expect(await settingsBtn.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return document.elementFromPoint(r.left + 2, r.top + 2) === el
    })).toBeTruthy()

    // The footer host is a real anchor (not a JS window.open): it opens the
    // bookmark URL in a new tab, so middle-click / right-click → copy link /
    // keyboard focus all work, and it must be the topmost (clickable) element
    // at its point while the popup is up.
    const hostLink = popup.getByTestId('bookmark-preview-popup-link')
    await expect(hostLink).toBeVisible()
    await expect(hostLink).toHaveAttribute('target', '_blank')
    await expect(hostLink).toHaveAttribute('href', 'https://example.com/uc-093-target')
    await expect(await hostLink.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return document.elementFromPoint(r.left + 2, r.top + r.height / 2) === el
    })).toBeTruthy()

    // The popup's own ⋯ trigger lives in the footer and must be visible — it
    // is the replacement for the row ⋯ that this popup covers.
    const footerMenu = popup.getByTestId('bookmark-menu-button')
    await expect(footerMenu).toBeVisible()

    // Open the footer menu and reach Edit. The row's covered ⋯ is no longer
    // the path; this proves the action surface in the footer is wired to the
    // same edit handler the row menu uses.
    await footerMenu.click()
    await page.getByRole('menuitem', { name: /edit/i }).click()
    await expect(page.locator('[role="dialog"]')).toBeVisible()
  })
})
