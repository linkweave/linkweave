import { expect, test } from './fixtures'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser
let storageState: StorageState
let collectionId: string

test.describe('Collection Management', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'colmanage',
    ))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test.beforeEach(async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()
  })

  test('should navigate to manage collections page', async ({ page }) => {
    await expect(page).toHaveURL(/\/manage\/collections/)
  })

  test('should go back to collection view when pressing back button', async ({ page }) => {
    // The back button uses router.go(-1); seed history with a collection
    // visit so back navigates somewhere meaningful (not about:blank).
    await page.goto(`/collections/${collectionId}`)
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()
    await manage.goBack()
  })

  test('should create a new collection', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const name = `Test Collection ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`

    await manage.createCollection(name)
    await manage.expectCollectionVisible(name)
  })

  test('should edit a collection name', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const originalName = `Edit Me ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`
    const updatedName = `Edited ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`

    await manage.createCollection(originalName)
    await manage.expectCollectionVisible(originalName)

    const collectionId = await manage.getCollectionIdByName(originalName)
    await manage.editCollection(collectionId, updatedName)

    await manage.expectCollectionVisible(updatedName)
    await manage.expectCollectionNotVisible(originalName)
  })

  test('should delete a collection', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const name = `Delete Me ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`

    await manage.createCollection(name)
    await manage.expectCollectionVisible(name)

    const collectionId = await manage.getCollectionIdByName(name)
    await manage.deleteCollection(collectionId, name)

    await manage.expectCollectionNotVisible(name)
  })

  test('should not delete collection if confirm name does not match', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const name = `No Delete ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`

    await manage.createCollection(name)
    const collectionId = await manage.getCollectionIdByName(name)

    await page.getByTestId(`collection-delete-btn-${collectionId}`).click()
    await manage.deleteConfirmInput.fill('wrong name')
    await expect(page.getByTestId('collection-delete-submit-btn')).toBeDisabled()
  })

  test('should set a collection as default', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    const name = `Make Default ${`${Date.now()}-${Math.random().toString(36).slice(2, 6)}`}`

    await manage.createCollection(name)
    const collectionId = await manage.getCollectionIdByName(name)

    await manage.setAsDefault(collectionId)
    await expect(manage.collectionSetDefaultBtn(collectionId)).not.toBeVisible()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
