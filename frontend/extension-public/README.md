# LinkWeave Extension — manifest notes

## `browser_specific_settings.gecko.id` — DO NOT rename

The ID `chainlink@markushofstetter.com` is the extension's **permanent identity**
on Mozilla AMO and in Firefox. It cannot be changed for an existing listing:

- AMO locks the add-on ID to the listing forever.
- Changing it makes AMO treat the next upload as a **brand-new add-on** — new
  listing, new (initial manual) review, new API credentials.
- Existing Firefox users on the old ID will **not** auto-update to the new one.

The string is internal — users never see it (they see `name` = "LinkWeave"), so
keeping `chainlink@…` costs nothing and preserves the listing, the automated
`web-ext sign` publish step in CI, and all existing users.

## Host permissions

- `host_permissions` lists only the canonical default instance (`linkweave.dev`)
  so the out-of-the-box config works without a runtime prompt.
- `optional_host_permissions: ["https://*/*"]` is requested at runtime via
  `chrome.permissions.request()` for a user's self-hosted origin (options Save
  click or the popup's Grant-Access button). HTTPS-only is deliberate.
- The **dev build** (`.env.extension.development`) targets the dev backend
  `dev-linkweave.dev`. It is
  **not** in `host_permissions` — by design, so the dev host never leaks into the
  published manifest — so `pnpm run dev:extension` goes through the same one-time
  runtime grant as a self-hoster (covered by `optional_host_permissions`).
