import { expect, type Locator, type Page } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class CollectionManagePageObject {
  readonly page: Page
  readonly backButton: Locator
  readonly createButton: Locator
  readonly createNameInput: Locator
  readonly editNameInput: Locator
  readonly editFaviconAllowlistInput: Locator
  readonly deleteConfirmInput: Locator

  constructor(page: Page) {
    this.page = page
    this.backButton = page.getByTestId('collection-manage-back-btn')
    this.createButton = page.getByTestId('collection-manage-create-btn')
    this.createNameInput = page.getByTestId('create-collection-name-input')
    this.editNameInput = page.getByTestId('edit-collection-name-input')
    this.editFaviconAllowlistInput = page.getByTestId('edit-collection-favicon-allowlist-input')
    this.deleteConfirmInput = page.getByTestId('delete-confirm-name-input')
  }

  async loginAndNavigate(email = 'alice@example.com', password = 'alice') {
    await new LoginPageObject(this.page).loginAndLand(email, password)
    await this.navigate()
  }

  async navigate() {
    await this.page.goto('/manage/collections')
    await expect(this.page).toHaveURL(/\/manage\/collections/, { timeout: 15000 })
    // createButton lives in the page header and renders on mount, independent
    // of the async collections fetch — a reliable render signal. The earlier
    // locator-based guard (`row | :text(noCollections)`) was doubly broken:
    // the `:text()` pseudo never matched (the empty state renders the
    // translated string, not the literal "noCollections"), and without an
    // explicit timeout it could wait the whole 90s test budget when the
    // collections GET was slow — then `.catch(() => {})` hid the failure and
    // the test ran out of time downstream.
    await expect(this.createButton).toBeVisible({ timeout: 20000 })
    // Also wait for the rows to actually render — otherwise subsequent
    // edit/delete button clicks race the list GET under parallel e2e load
    // and the click spends its whole budget waiting for the element. The
    // empty-state text matches the i18n key `collectionManage.noCollections`.
    await this.page
      .locator('[data-testid^="collection-row-"]')
      .or(this.page.getByText('No collections found.'))
      .first()
      .waitFor({ state: 'visible', timeout: 30000 })
  }

  async getCollectionIdByName(name: string): Promise<string> {
    const row = this.page.locator('[data-testid^="collection-row-"]', { hasText: name })
    // Bounded wait — under parallel load the row can lag, but the previous
    // default (90s = whole test) just hid the real failure downstream.
    await expect(row).toBeVisible({ timeout: 15000 })
    const testId = await row.getAttribute('data-testid')
    return testId!.replace('collection-row-', '')
  }

  async createCollection(name: string) {
    await this.createButton.click()
    await expect(this.createNameInput).toBeVisible({ timeout: 10000 })
    await this.createNameInput.fill(name)
    const submitBtn = this.page.getByTestId('collection-create-submit-btn')
    await expect(submitBtn).toBeEnabled()
    const createResp = this.page.waitForResponse(
      (r) => r.url().endsWith('/api/collections') && r.request().method() === 'POST',
      { timeout: 15000 },
    )
    await submitBtn.click()
    const resp = await createResp
    if (!resp.ok()) {
      throw new Error(`createCollection POST failed: ${resp.status()}`)
    }
    // The dialog only closes after createCollection's follow-up fetchCollections
    // completes. Under heavy parallel load the list-fetch can be slow, so use a
    // generous timeout instead of the default 5s.
    await expect(this.createNameInput).not.toBeVisible({ timeout: 20000 })
    // The dialog close fires before the new row renders (or before the list
    // GET settles under parallel load). Wait for the row directly with a
    // generous budget + reload fallback. The POST already returned ok, so the
    // collection exists in the backend; under heavy parallel load the only
    // reasons the row can be missing are a silently-failed list GET or stale
    // data, both of which a reload fixes.
    await this.expectCollectionVisible(name, { timeout: 30000, allowReload: true })
  }

  async editCollection(collectionId: string, newName: string) {
    await this.openEditDialog(collectionId)
    await this.editNameInput.clear()
    await this.editNameInput.fill(newName)
    await this.submitEditDialog()
  }

  async openEditDialog(collectionId: string) {
    // The dialog opens, then asynchronously fetches the collection and resets
    // the form. Wait for that GET so user input isn't clobbered by the late
    // resetForm. Bounded timeout + swallow: callers that care about the
    // post-reset form state (favicon-allowlist) follow up with a `toHaveValue`
    // assertion that is the real signal — if the GET genuinely failed, that
    // assertion will surface the bug. Treating the wait as hard-fatal here
    // caused flakes under parallel load when the click was momentarily slow
    // and the response arrived just outside the budget.
    const collectionGet = this.page.waitForResponse(
      (r) => r.url().includes(`/api/collections/${collectionId}`) && r.request().method() === 'GET',
      { timeout: 20000 },
    )
    await this.page.getByTestId(`collection-edit-btn-${collectionId}`).click()
    await expect(this.editNameInput).toBeVisible({ timeout: 10000 })
    await collectionGet.catch(() => undefined)
  }

  /**
   * Opens the edit dialog and waits for the async collection-info fetch
   * to complete so the favicon-allowlist textarea reflects the persisted value.
   *
   * CRITICAL: this must NOT return before the async resetForm has applied.
   * The previous implementation only polled `editFaviconAllowlistInput` for
   * the expected value — but the dialog's initial reset (on open) already
   * sets the allowlist to '', so an `expectedValue === ''` check matched
   * instantly, before the GET arrived. A late-arriving GET then reset the
   * form AGAIN and clobbered the caller's subsequent fill. We now register
   * our own strict response wait so we know the GET has truly landed.
   */
  async openEditDialogAndWaitForAllowlist(collectionId: string, expectedValue?: string) {
    // Wait for the edit button BEFORE setting up the response wait — under
    // parallel e2e load the specific row can lag navigate()'s "any row
    // visible" guard, and a slow click would otherwise eat into the response
    // timeout. One-shot reload fallback: if the row genuinely isn't there
    // after 30s (e.g. silently failed list GET), reload forces a fresh fetch.
    const editBtn = this.page.getByTestId(`collection-edit-btn-${collectionId}`)
    try {
      await expect(editBtn).toBeVisible({ timeout: 30000 })
    } catch {
      await this.page.reload()
      await expect(this.createButton).toBeVisible({ timeout: 20000 })
      await expect(editBtn).toBeVisible({ timeout: 30000 })
    }
    // Register the response listener BEFORE clicking so we don't miss it.
    const collectionGet = this.page.waitForResponse(
      (r) => r.url().includes(`/api/collections/${collectionId}`) && r.request().method() === 'GET',
      { timeout: 30000 },
    )
    await editBtn.click()
    await expect(this.editNameInput).toBeVisible({ timeout: 10000 })
    // Strictly await the GET: if it didn't land in 30s, that's a real backend
    // issue we want surfaced, not swallowed.
    await collectionGet
    await expect(this.editFaviconAllowlistInput).toBeVisible({ timeout: 5000 })
    if (expectedValue !== undefined) {
      // resetForm fires synchronously after the awaited GET above, so the
      // textarea value settles within a frame or two. A short budget is
      // plenty — the previous 10s timeout was a stacked budget that could
      // eat the test's remaining time during a slow patch.
      await expect(this.editFaviconAllowlistInput).toHaveValue(expectedValue, { timeout: 5000 })
    }
  }

  async submitEditDialog() {
    await this.page.getByTestId('collection-edit-submit-btn').click()
    await expect(this.editNameInput).not.toBeVisible({ timeout: 15000 })
  }

  async editFaviconAllowlist(collectionId: string, allowlist: string) {
    await this.openEditDialog(collectionId)
    await expect(this.editFaviconAllowlistInput).toBeVisible({ timeout: 5000 })
    await this.editFaviconAllowlistInput.clear()
    if (allowlist) {
      await this.editFaviconAllowlistInput.fill(allowlist)
    }
    await this.submitEditDialog()
  }

  async deleteCollection(collectionId: string, collectionName: string) {
    await this.page.getByTestId(`collection-delete-btn-${collectionId}`).click()
    await expect(this.deleteConfirmInput).toBeVisible({ timeout: 10000 })
    await this.deleteConfirmInput.fill(collectionName)
    const submitBtn = this.page.getByTestId('collection-delete-submit-btn')
    await expect(submitBtn).toBeEnabled()
    const deleteResp = this.page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}`) && r.request().method() === 'DELETE',
      { timeout: 15000 },
    )
    await submitBtn.click()
    await deleteResp
    await expect(this.deleteConfirmInput).not.toBeVisible({ timeout: 20000 })
  }

  async setAsDefault(collectionId: string) {
    await this.page.getByTestId(`collection-set-default-btn-${collectionId}`).click()
  }

  collectionSetDefaultBtn(collectionId: string): Locator {
    return this.page.getByTestId(`collection-set-default-btn-${collectionId}`)
  }

  async expectCollectionVisible(name: string, opts: { timeout?: number; allowReload?: boolean } = {}) {
    const { timeout = 15000, allowReload = false } = opts
    const row = this.page.locator('[data-testid^="collection-row-"]', { hasText: name })
    try {
      await expect(row).toBeVisible({ timeout })
    } catch (e) {
      if (!allowReload) throw e
      // One-shot reload fallback: under parallel load the list GET can silently
      // fail or return stale data, leaving the row missing even though the
      // backend has it. Reload forces a fresh fetch.
      await this.page.reload()
      await expect(this.createButton).toBeVisible({ timeout: 20000 })
      await expect(row).toBeVisible({ timeout })
    }
  }

  async expectCollectionNotVisible(name: string) {
    await expect(
      this.page.locator('[data-testid^="collection-row-"]', { hasText: name }),
    ).not.toBeVisible({ timeout: 15000 })
  }

  async goBack() {
    await this.backButton.click()
    await expect(this.page).toHaveURL(/\/collections\//, { timeout: 15000 })
  }
}
