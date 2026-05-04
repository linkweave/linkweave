import { test } from '@playwright/test'
import { TagsPageObject } from './models/TagsPageObject'
import { deleteTestUserCleanup, registerTestUser, type TestUser } from './models/TestUser'

let user: TestUser

test.describe('Tag Management', () => {
  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      user = await registerTestUser(ctx.request, 'tags')
    } finally {
      await ctx.close()
    }
  })

  test('should add, edit, and remove a tag', async ({ page }) => {
    const tagsPage = new TagsPageObject(page)
    const tagName = `test-tag-${Date.now()}`
    const updatedTagName = `${tagName}-updated`

    await tagsPage.loginAndWaitForPage(user.email, user.password)

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
