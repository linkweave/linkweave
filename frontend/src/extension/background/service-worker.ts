// Runs as a MV3 service worker. No DOM access, no Vue.
// Responsibilities: register context menu, handle clicks.

// Use chrome.storage.local instead of session for Firefox compatibility.
// Session storage is Chrome 102+ only; local storage works everywhere.
const CONTEXT_MENU_KEY = '_lw_contextMenuUrl'

chrome.runtime.onInstalled.addListener(async () => {
  // Clear any stale items first — e.g. the legacy 'chainlink-save' id from
  // pre-rename versions — so updates don't accumulate duplicate menu entries.
  // removeAll() rarely rejects; create the menu regardless so it never vanishes.
  try {
    await chrome.contextMenus.removeAll()
  } catch {
    /* ignore — proceed to (re)create the menu */
  }
  chrome.contextMenus.create({
    id: 'linkweave-save',
    title: 'Add to LinkWeave',
    contexts: ['page', 'link'],
  })

  // One-shot cleanup of the pre-rename storage key (_cl_ -> _lw_).
  chrome.storage.local.remove('_cl_contextMenuUrl').catch(() => {})
})

chrome.contextMenus.onClicked.addListener((info) => {
  // For links use linkUrl, otherwise use the page URL
  const url = info.linkUrl ?? info.pageUrl ?? ''
  // Store the URL so the popup can read it on open
  chrome.storage.local.set({ [CONTEXT_MENU_KEY]: url }).then(() => {
    // Open the popup — works in Chrome when triggered from user gesture
    chrome.action.openPopup().catch(() => {
      // Fallback: open the popup URL directly as a tab (Firefox, or when openPopup fails)
      chrome.tabs.create({ url: chrome.runtime.getURL('popup.html') })
    })
  })
})
