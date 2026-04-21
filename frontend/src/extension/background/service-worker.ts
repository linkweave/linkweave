// Runs as a MV3 service worker. No DOM access, no Vue.
// Responsibilities: register context menu, handle clicks.

// Use chrome.storage.local instead of session for Firefox compatibility.
// Session storage is Chrome 102+ only; local storage works everywhere.
const CONTEXT_MENU_KEY = '_cl_contextMenuUrl'

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'chainlink-save',
    title: 'Add to Chainlink',
    contexts: ['page', 'link'],
  })
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
