import { expect, test } from './fixtures'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser
let collectionId: string
let storageState: StorageState

test.describe('Collection layout toggle', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'collectionlayout',
    ))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('toggle is rendered with three options and a default pressed', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)

    const group = page.getByRole('group', { name: /bookmark layout/i })
    await expect(group).toBeVisible()
    await expect(group.getByRole('button', { name: 'List' })).toBeVisible()
    await expect(group.getByRole('button', { name: 'Grid' })).toBeVisible()
    await expect(group.getByRole('button', { name: 'Grouped' })).toBeVisible()

    // Exactly one button is pressed at any time.
    const pressed = group.getByRole('button', { pressed: true })
    await expect(pressed).toHaveCount(1)
  })

  test('clicking a layout updates the pressed state and persists across reload', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    const group = page.getByRole('group', { name: /bookmark layout/i })

    await group.getByRole('button', { name: 'List' }).click()
    await expect(group.getByRole('button', { name: 'List' })).toHaveAttribute('aria-pressed', 'true')

    // Wait for the debounced PUT to settle so the value reaches the backend
    // before we reload the page.
    await page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings`) &&
        r.request().method() === 'PUT' &&
        r.ok(),
    )

    await page.reload()
    const reloadedGroup = page.getByRole('group', { name: /bookmark layout/i })
    await expect(reloadedGroup.getByRole('button', { name: 'List' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  test('rapid clicks are debounced into a single PUT carrying the final value', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    const group = page.getByRole('group', { name: /bookmark layout/i })

    // Start from a known state and let the resulting PUT settle.
    await group.getByRole('button', { name: 'Grid' }).click()
    await page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings`) &&
        r.request().method() === 'PUT' &&
        r.ok(),
    )

    // Now record only the PUTs that fire from here on.
    const settingsPuts: { body: string }[] = []
    page.on('request', (req) => {
      if (
        req.method() === 'PUT' &&
        req.url().includes(`/api/collections/${collectionId}/settings`)
      ) {
        settingsPuts.push({ body: req.postData() ?? '' })
      }
    })

    // Three clicks issued in a single microtask — well inside the 400ms debounce window.
    await page.evaluate(() => {
      const buttons = Array.from(
        document.querySelectorAll<HTMLButtonElement>(
          '[role="group"][aria-label="Bookmark Layout"] button',
        ),
      )
      const byTitle = (t: string) => buttons.find((b) => b.title.startsWith(t))
      byTitle('List')?.click()
      byTitle('Grouped')?.click()
      byTitle('Grid')?.click()
    })

    // Wait for the single coalesced PUT.
    await page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings`) &&
        r.request().method() === 'PUT' &&
        r.ok(),
    )
    // Give any (unwanted) trailing requests a chance to also fire.
    await page.waitForTimeout(700)

    expect(settingsPuts).toHaveLength(1)
    expect(settingsPuts[0].body).toContain('"layout":"grid"')

    // The final UI state matches the last click.
    await expect(group.getByRole('button', { name: 'Grid' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
