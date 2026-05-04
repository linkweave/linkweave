import { expect, test } from '@playwright/test'
import { TagsPageObject } from './models/TagsPageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

let user: TestUser
let storageState: StorageState
let collectionId: string

test.describe('Tag Management', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(browser, 'tags'))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('should add, edit, and remove a tag', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    const tagName = `test-tag-${Date.now()}`
    const updatedTagName = `${tagName}-updated`

    await page.goto(`/collections/${collectionId}`)
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))

    await tagsPage.createTag(tagName)
    await tagsPage.expectTagVisible(tagName)

    await tagsPage.editTag(tagName, updatedTagName)
    await tagsPage.expectTagVisible(updatedTagName)
    await tagsPage.expectTagNotVisible(tagName)

    await tagsPage.deleteTag(updatedTagName)
    await tagsPage.expectTagNotVisible(updatedTagName)
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
