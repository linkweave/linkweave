import { expect, type Page, test } from './fixtures'
import { createBookmarkViaApi, createCollectionViaApi } from './helpers/api'
import { login } from './helpers/auth'

// Import review — UC-096: parse → review tree → select → commit. The review
// surface lets the user deselect folders/bookmarks and skip duplicates before
// anything is written. Tests share a seeded collection, so run in order.
test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const collectionName = `Import Review ${ts}`
const dupUrl = `https://dup-${ts}.example.com`
const keptUrl = `https://kept-${ts}.example.com`
const rootUrl = `https://root-${ts}.example.com`

const folderName = `WorkE2E ${ts}`
const dupBook = `DupBook ${ts}`
const keptBook = `KeptBook ${ts}`
const rootBook = `RootBook ${ts}`

let collectionId: string

// Netscape bookmark HTML: a folder with a duplicate + a new bookmark, plus a
// root-level bookmark.
function bookmarksHtml(): string {
  // keptBook + rootBook at root (so they're visible on the collection root
  // after import, independent of folder-view semantics); the duplicate lives in
  // a folder.
  return `<!DOCTYPE NETSCAPE-Bookmark-file-1>
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><A HREF="${keptUrl}">${keptBook}</A>
    <DT><A HREF="${rootUrl}">${rootBook}</A>
    <DT><H3>${folderName}</H3>
    <DL><p>
        <DT><A HREF="${dupUrl}">${dupBook}</A>
    </DL><p>
</DL><p>`
}

async function uploadAndAwaitTree(page: Page) {
  await login(page)
  await page.goto(`/collections/${collectionId}/import`)
  await page.getByTestId('import-file-input').setInputFiles({
    name: 'bookmarks.html',
    mimeType: 'text/html',
    buffer: Buffer.from(bookmarksHtml(), 'utf-8'),
  })
  await expect(page.getByTestId(`import-folder-${folderName}`)).toBeVisible()
}

function bookmarkRow(page: Page, title: string) {
  return page.getByTestId(`import-bookmark-${title}`)
}

test.describe('Import review (UC-096)', () => {
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      collectionId = await createCollectionViaApi(page.request, collectionName)
      // Pre-existing bookmark whose URL collides with dupBook in the import file.
      await createBookmarkViaApi(page.request, collectionId, `Existing ${ts}`, dupUrl)
    } finally {
      await context.close()
    }
  })

  test('flags duplicates and pre-deselects them; skip pill toggles them', async ({ page }) => {
    await uploadAndAwaitTree(page)

    // Duplicate is flagged and pre-deselected; the others are selected.
    await expect(bookmarkRow(page, dupBook)).toHaveAttribute('data-duplicate', 'true')
    await expect(bookmarkRow(page, dupBook)).toHaveAttribute('data-selected', 'false')
    await expect(bookmarkRow(page, keptBook)).toHaveAttribute('data-selected', 'true')
    await expect(bookmarkRow(page, rootBook)).toHaveAttribute('data-selected', 'true')

    // Footer reflects 2 of 3 (duplicate excluded).
    await expect(page.getByTestId('import-review-submit')).toContainText('2')

    // Turning off "skip" re-includes the duplicate → 3 selected.
    await page.getByTestId('import-skip-duplicates').uncheck()
    await expect(bookmarkRow(page, dupBook)).toHaveAttribute('data-selected', 'true')
    await expect(page.getByTestId('import-review-submit')).toContainText('3')

    // Turning it back on deselects it again.
    await page.getByTestId('import-skip-duplicates').check()
    await expect(bookmarkRow(page, dupBook)).toHaveAttribute('data-selected', 'false')
    await expect(page.getByTestId('import-review-submit')).toContainText('2')
  })

  test('imports only the selected bookmarks and navigates to the collection', async ({ page }) => {
    await uploadAndAwaitTree(page)

    // Keep only keptBook: deselect the root bookmark (dup already skipped).
    await bookmarkRow(page, rootBook).getByRole('checkbox').click()
    await expect(bookmarkRow(page, rootBook)).toHaveAttribute('data-selected', 'false')
    await expect(page.getByTestId('import-review-submit')).toContainText('1')

    await page.getByTestId('import-review-submit').click()

    // Lands on the collection; the kept bookmark is present, the deselected one is not.
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
    await expect(page.locator(`[data-bookmark-title="${keptBook}"]`)).toBeVisible()
    await expect(page.locator(`[data-bookmark-title="${rootBook}"]`)).toHaveCount(0)
  })

  // Reproduces the reported bug: importing the same file twice must flag every
  // bookmark as already-in-library on the second pass — including URLs with
  // percent-encoded queries, which previously slipped through.
  test('flags every bookmark as duplicate when the same file is imported twice', async ({
    page,
  }) => {
    const encUrl = `https://auth-${ts}.example.com/login?return_url=https%3A%2F%2Fauth-${ts}.example.com%2Fapps`
    const plainTitle = `Plain ${ts}`
    const encTitle = `Encoded ${ts}`
    const html = `<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>
      <DT><A HREF="https://plain-${ts}.example.com/page">${plainTitle}</A>
      <DT><A HREF="${encUrl}">${encTitle}</A>
    </DL><p>`

    async function upload() {
      await page.goto(`/collections/${collectionId}/import`)
      await page.getByTestId('import-file-input').setInputFiles({
        name: 'twice.html',
        mimeType: 'text/html',
        buffer: Buffer.from(html, 'utf-8'),
      })
      await expect(bookmarkRow(page, plainTitle)).toBeVisible()
    }

    await login(page)

    // First pass: nothing is a duplicate yet → import both.
    await upload()
    await expect(bookmarkRow(page, plainTitle)).toHaveAttribute('data-duplicate', 'false')
    await expect(bookmarkRow(page, encTitle)).toHaveAttribute('data-duplicate', 'false')
    await page.getByTestId('import-review-submit').click()
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))

    // Second pass: both are now in the library — including the encoded URL.
    await upload()
    await expect(bookmarkRow(page, plainTitle)).toHaveAttribute('data-duplicate', 'true')
    await expect(bookmarkRow(page, encTitle)).toHaveAttribute('data-duplicate', 'true')
    await expect(bookmarkRow(page, plainTitle)).toHaveAttribute('data-selected', 'false')
    await expect(bookmarkRow(page, encTitle)).toHaveAttribute('data-selected', 'false')
  })
})
