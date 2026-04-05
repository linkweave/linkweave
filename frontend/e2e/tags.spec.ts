import { expect, test } from '@playwright/test'
import { TagsPageObject } from './models/TagsPageObject'

test.describe('UC-015: Create Tag', () => {
  test('should create a new tag', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    await tagsPage.loginAndWaitForPage()

    const tagName = `create-tag-${Date.now()}`
    await tagsPage.createTag(tagName)
    await tagsPage.expectTagVisible(tagName)
  })
})

test.describe('UC-016: View Tags', () => {
  test('should display tags in the sidebar', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    await tagsPage.loginAndWaitForPage()

    const tagName = `view-tag-${Date.now()}`
    await tagsPage.createTag(tagName)
    await tagsPage.expectTagVisible(tagName)
  })
})

test.describe('UC-017: Edit Tag', () => {
  test('should edit a tag name', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    await tagsPage.loginAndWaitForPage()

    const tagName = `edit-tag-${Date.now()}`
    const updatedTagName = `${tagName}-updated`

    await tagsPage.createTag(tagName)
    await tagsPage.editTag(tagName, updatedTagName)
    await tagsPage.expectTagVisible(updatedTagName)
    await tagsPage.expectTagNotVisible(tagName)
  })
})

test.describe('UC-018: Delete Tag', () => {
  test('should delete a tag', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    await tagsPage.loginAndWaitForPage()

    const tagName = `delete-tag-${Date.now()}`
    await tagsPage.createTag(tagName)
    await tagsPage.expectTagVisible(tagName)

    await tagsPage.deleteTag(tagName)
    await tagsPage.expectTagNotVisible(tagName)
  })
})
