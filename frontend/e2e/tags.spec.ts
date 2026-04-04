import { test } from '@playwright/test'
import { TagsPageObject } from './models/TagsPageObject'

test.describe('Tag Management', () => {
  test('should add, edit, and remove a tag', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    const tagName = `test-tag-${Date.now()}`
    const updatedTagName = `${tagName}-updated`

    await tagsPage.loginAndWaitForPage()

    // 1. Add a tag
    await tagsPage.createTag(tagName)
    await tagsPage.expectTagVisible(tagName)

    // 2. Edit the tag
    await tagsPage.editTag(tagName, updatedTagName)
    await tagsPage.expectTagVisible(updatedTagName)
    await tagsPage.expectTagNotVisible(tagName)

    // 3. Remove the tag
    await tagsPage.deleteTag(updatedTagName)
    await tagsPage.expectTagNotVisible(updatedTagName)
  })
})
