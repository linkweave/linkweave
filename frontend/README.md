# LinkWeave Frontend

Vue.js frontend and browser extension for [LinkWeave](https://linkweave.dev), a self-hosted bookmark manager.

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | ^20.19.0 or >=22.12.0 |
| npm | Included with Node.js |
| OS | macOS, Linux, or Windows |

Verify versions:

```sh
node --version   # v20.19+ or v22.12+
npm --version    # any compatible version
```

## Project Setup

```sh
cd frontend
npm ci
```

## Web App

### Development

```sh
npm run dev
```

### Production Build

```sh
npm run build
```

### Type Checking

```sh
npm run type-check
```

### Linting

```sh
npm run lint
```

### Unit Tests

```sh
npm test
```

### End-to-End Tests

```sh
npm run test:e2e
```

## Browser Extension

The browser extension is built from `src/extension/` using a separate Vite config. It produces a Manifest V3 extension compatible with Firefox and Chrome.

### Development (watch mode)

```sh
npm run dev:extension
```

Then load `dist-extension/` as a temporary add-on:
- **Firefox:** `about:debugging#/runtime/this-firefox` → Load Temporary Add-on → select `dist-extension/manifest.json`
- **Chrome:** `chrome://extensions` → Enable Developer mode → Load unpacked → select `dist-extension/`

### Production Build

```sh
npm run build:extension
```

Output is written to `dist-extension/`.

### Package for Distribution

```sh
npm run package:extension
```

This runs the production build and creates a timestamped ZIP using `web-ext build` (e.g. `linkweave-ext-20260504-133855.zip`), suitable for uploading to AMO or Chrome Web Store.

### Lint Extension

```sh
npx web-ext lint --source-dir dist-extension
```

### Configuration

On first use, configure the extension via the options page (right-click the extension icon → Options). Set:

- **API URL** — Base URL of your LinkWeave API instance (e.g. `https://linkweave.example.com`)
- **Web App URL** — URL of the LinkWeave web app (used for the sign-in link)

These are stored in `chrome.storage.sync` and can be changed at any time.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)
