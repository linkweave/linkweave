import { expect, test } from '@playwright/test'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'
import { ShareCollectionPageObject } from './models/ShareCollectionPageObject'
import { deleteTestUserCleanup, registerTestUser, type TestUser } from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser

test.describe('Collection Sharing', () => {
  let collectionId: string
  const collectionName = `Share Test ${Date.now()}-${Math.random().toString(36).slice(2, 6)}`

  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      user = await registerTestUser(ctx.request, 'colsharing')
    } finally {
      await ctx.close()
    }
  })

  test('should create a collection and open share dialog', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate(user.email, user.password)
    await manage.createCollection(collectionName)
    await manage.expectCollectionVisible(collectionName)

    collectionId = await manage.getCollectionIdByName(collectionName)

    await share.openShareDialog(collectionId)
    // Owner is the freshly-registered test user (vorname=E2E, nachname=<slug>).
    await share.expectOwnerVisible('E2E colsharing')
    await share.expectNoMembersMessage()
  })

  test('should show share button only for owned collections', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.loginAndNavigate(user.email, user.password)
    await expect(page.getByTestId(`collection-share-btn-${collectionId}`)).toBeVisible()
  })

  test('should display invite form in share dialog', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate(user.email, user.password)
    await share.openShareDialog(collectionId)

    await expect(share.emailInput).toBeVisible()
    await expect(share.inviteBtn).toBeVisible()
    await expect(share.inviteBtn).toBeDisabled()
  })

  test('should enable invite button when email is entered', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate(user.email, user.password)
    await share.openShareDialog(collectionId)

    // alice@example.com is a stable seeded invitee.
    await share.emailInput.fill('alice@example.com')
    await expect(share.inviteBtn).toBeEnabled()
  })

  test('should show email validation error on invalid email', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate(user.email, user.password)
    await share.openShareDialog(collectionId)

    await share.emailInput.fill('not-an-email')
    await share.emailInput.blur()

    await expect(page.getByText(/invalid email/i)).toBeVisible()
  })

  // Cleanup handled by user-delete in afterAll.
  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
