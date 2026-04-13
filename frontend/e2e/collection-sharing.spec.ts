import {expect, test} from '@playwright/test'
import {CollectionManagePageObject} from './models/CollectionManagePageObject'
import {ShareCollectionPageObject} from './models/ShareCollectionPageObject'

test.describe.configure({ mode: 'serial' })

test.describe('Collection Sharing', () => {
  let collectionId: string
  const collectionName = `Share Test ${Date.now()}-${Math.random().toString(36).slice(2, 6)}`

  test('should create a collection and open share dialog', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate()
    await manage.createCollection(collectionName)
    await manage.expectCollectionVisible(collectionName)

    collectionId = await manage.getCollectionIdByName(collectionName)

    await share.openShareDialog(collectionId)
    await share.expectOwnerVisible('Alice User')
    await share.expectNoMembersMessage()
  })

  test('should show share button only for owned collections', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.loginAndNavigate()
    await expect(page.getByTestId(`collection-share-btn-${collectionId}`)).toBeVisible()
  })

  test('should display invite form in share dialog', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate()
    await share.openShareDialog(collectionId)

    await expect(share.emailInput).toBeVisible()
    await expect(share.inviteBtn).toBeVisible()
    await expect(share.inviteBtn).toBeDisabled()
  })

  test('should enable invite button when email is entered', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const share = new ShareCollectionPageObject(page)

    await manage.loginAndNavigate()
    await share.openShareDialog(collectionId)

    await share.emailInput.fill('test@example.com')
    await expect(share.inviteBtn).toBeEnabled()
  })

  test('should clean up: delete test collection', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.loginAndNavigate()
    await manage.deleteCollection(collectionId, collectionName)
    await manage.expectCollectionNotVisible(collectionName)
  })
})
