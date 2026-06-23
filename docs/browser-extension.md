# Browser Extension Architecture Plan

**Project:** LinkWeave Browser Extension  
**Date:** 2026-04-04  
**Status:** Implemented

---

## 1. Overview

A Chrome/Firefox browser extension that lets users save the current page as a bookmark to their LinkWeave collection. The extension popup pre-fills URL and title from the active tab, and lets the user choose a collection, folder, and tags before saving.

### Scope

- Save current page URL (full URL or domain-only) with title
- Select target collection (defaults to user's default collection)
- Select target folder (optional)
- Select tags (optional, multi-select)
- Login through the extension popup
- Success/error feedback
- Context menu ("Right-click → Add to LinkWeave")

### Out of Scope (Future)

- Offline support
- Bookmark editing/deletion from extension
- API token generation
- Content script page overlay
- Multiple accounts

---

## 2. Existing Codebase Analysis

### 2.1 Patterns & Conventions Found

| Area | Pattern | Reference |
|------|---------|-----------|
| **Auth** | Form-based (`j_security_check`), cookie session | `application.properties:69-77`, `LoginView.vue:28-36` |
| **API Root** | All REST endpoints under `/api/` | `application.properties:5` |
| **Auth Check** | `@Authenticated` on every resource, `AuthorizationService` for collection access | `BookmarkResource.java:28`, `AuthorizationService.java` |
| **Bookmark Creation** | `POST /api/bookmarks` with `BookmarkSaveJson` | `BookmarkResource.java:42-47` |
| **Collection Info** | `GET /api/collections/{id}` returns bookmarks + folders + tags | `CollectionInfoJson.java`, `CollectionResource.java:23-26` |
| **User Info** | `GET /api/auth/me` returns `UserInfoJson` with `defaultCollectionId` | `AuthResource.java:36-52` |
| **API Client** | Generated TypeScript fetch client from OpenAPI | `frontend/src/api/generated/`, `frontend/scripts/generate-api.ts` |
| **Cookie Domain** | `linkweave.dev` (prod), `local-linkweave.localhost` (dev) | `application.properties:74-75` |
| **SSL** | HTTPS in dev with local certs | `application.properties:28-30` |
| **IDs** | Custom `ID<T>` type, serialized as UUID strings | `AbstractEntity.java`, `ID.java` |
| **Error handling** | `AppAuthException` → 401, `AppAuthorizationException` → 403, `AppValidationException` → 400 | Exception mappers in `infrastructure/exceptionmapper/` |

### 2.2 Relevant Existing API Endpoints

The extension will use these **existing** endpoints — **no new API endpoints are needed**:

| Endpoint | Method | Purpose | Request/Response |
|----------|--------|---------|------------------|
| `/api/j_security_check` | POST | Login (form-based) | Form data `j_username`, `j_password` → 200/401 |
| `/api/auth/me` | GET | Get current user + default collection ID | → `UserInfoJson` |
| `/api/collections/{id}` | GET | Get collection with folders, tags, bookmarks | → `CollectionInfoJson` |
| `/api/bookmarks` | POST | Create bookmark | `BookmarkSaveJson` → `BookmarkJson` |
| `/api/tags` | GET | List tags for collection | `?collectionId=X` → `TagListJson` |
| `/api/folders` | GET | List folders for collection | `?collectionId=X` → `FolderListJson` |

### 2.3 Key DTOs

```
BookmarkSaveJson {
  collectionId: string (UUID)    // required
  folderId?: string (UUID)       // optional
  title: string                  // required
  url: string                    // required, must be valid URL
  description?: string           // optional
  tagIds?: Set<string>           // optional, UUIDs
}

UserInfoJson {
  email: string
  firstName: string
  lastName: string
  roles: Set<string>
  defaultCollectionId: string (UUID)
}

TagJson { id, entityInfo, data: { collectionId, name, color } }
FolderJson { id, entityInfo, data: { collectionId, parentId?, name } }
```

---

## 3. Architecture Decision

### 3.1 Authentication Strategy

**Decision: Reuse existing form-based cookie auth via `host_permissions`.**

**Rationale:**
- The backend already has form-based auth with cookie sessions (`quarkus.http.auth.form.enabled=true`)
- Browser extensions with `host_permissions` for the LinkWeave domain can make credentialed fetch requests that include cookies for that domain
- No new auth mechanism (tokens, API keys) is needed for v1
- The user logs in through the extension popup → the session cookie is set on the LinkWeave domain → all subsequent API calls include it automatically

**How it works:**

```
┌─────────────────────────────────────────────────────────────────────┐
│ Extension Popup / Service Worker                                    │
│                                                                     │
│  1. User enters credentials in popup                                │
│  2. POST /api/j_security_check { credentials: 'include' }           │
│     → Server sets session cookie on linkweave domain                │
│  3. All subsequent fetch() calls with credentials: 'include'        │
│     → Browser includes the session cookie automatically             │
│                                                                     │
│  Auth check: GET /api/auth/me                                       │
│     → 200 = authenticated, response contains defaultCollectionId    │
│     → 401 = not authenticated, show login form                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Trade-offs:**
- ✅ Zero backend auth changes needed
- ✅ Session cookie is already HttpOnly and encrypted
- ✅ Same security model as the web app
- ⚠️ User must be logged in via the extension (separate from web app login in some browsers)
- ⚠️ Session expiry requires re-login
- 📋 Future: API tokens for headless/CLI use cases (not in scope)

### 3.2 CORS Strategy

**Decision: Add a CORS filter to the Quarkus backend.**

While Chrome extensions with `host_permissions` can bypass CORS in some contexts, adding proper CORS headers ensures:
- Firefox compatibility (stricter CORS enforcement)
- Predictable behavior across extension contexts (popup vs. service worker vs. content script)
- Future-proofing for web-based extension pages

The CORS filter will allow requests from:
- The web app's own origin (already works, but explicit is better)
- `chrome-extension://*` origins
- `moz-extension://*` origins

### 3.3 Extension Architecture

**Decision: Manifest V3, TypeScript, Vite build, no UI framework.**

The extension is lightweight — a single popup with a form. Using Vue would add bundle size for little benefit. Vanilla TypeScript with a simple reactive pattern keeps the extension fast and small.

---

## 4. Extension Component Design

### 4.1 Directory Structure

```
extension/
├── manifest.json                    # Extension manifest (MV3)
├── package.json                     # Extension dependencies
├── tsconfig.json                    # TypeScript config
├── vite.config.ts                   # Vite build config
├── src/
│   ├── popup/
│   │   ├── popup.html               # Popup HTML entry
│   │   ├── popup.ts                 # Popup script entry
│   │   ├── popup.css                # Popup styles
│   │   ├── views/
│   │   │   ├── login-view.ts        # Login form rendering
│   │   │   ├── save-view.ts         # Bookmark save form rendering
│   │   │   └── success-view.ts      # Success confirmation
│   │   └── components/
│   │       ├── folder-select.ts     # Folder dropdown
│   │       └── tag-select.ts        # Tag multi-select
│   ├── background/
│   │   └── service-worker.ts        # Background service worker
│   ├── api/
│   │   ├── linkweave-client.ts      # HTTP client wrapper
│   │   └── types.ts                 # API type definitions (mirrors DTOs)
│   ├── auth/
│   │   └── auth-service.ts          # Auth state management
│   ├── storage/
│   │   └── extension-storage.ts     # Chrome storage helpers (settings cache)
│   └── lib/
│       └── dom.ts                   # Minimal DOM helpers for reactive UI
├── assets/
│   └── icons/
│       ├── icon-16.png
│       ├── icon-48.png
│       └── icon-128.png
└── dist/                            # Build output
```

### 4.2 Component Responsibilities

#### `manifest.json`
- Declares MV3 permissions: `activeTab`, `cookies`, `contextMenus`, `storage`
- Declares `host_permissions` for the LinkWeave domain
- Registers popup, service worker, and icons

#### `service-worker.ts`
- Registers context menu item ("Add to LinkWeave")
- Handles context menu clicks → opens popup with URL pre-filled
- Listens for messages from popup
- Manages the extension lifecycle

#### `linkweave-client.ts`
- Centralized HTTP client for all API communication
- Handles base URL configuration (dev vs. prod)
- Attaches `credentials: 'include'` to all requests
- Throws structured errors on non-2xx responses
- Methods: `login()`, `getCurrentUser()`, `getCollection()`, `createBookmark()`, `getTags()`, `getFolders()`

#### `auth-service.ts`
- Manages auth state: `isAuthenticated`, `user`, `defaultCollectionId`
- `checkAuth()`: calls `GET /api/auth/me`, updates state
- `login(email, password)`: calls `POST /api/j_security_check`
- `logout()`: calls `POST /api/auth/logout`
- Caches auth state in `chrome.storage.session` for popup lifetime

#### `popup.ts`
- Entry point for the popup
- On open: calls `authService.checkAuth()`
- If authenticated → renders `save-view.ts`
- If not authenticated → renders `login-view.ts`
- Reads current tab URL and title via `chrome.tabs.query()`

#### `save-view.ts`
- Pre-fills URL and title from active tab
- Offers toggle: full URL vs. domain-only
- Collection selector (default pre-selected)
- Folder dropdown (loaded from API, optional)
- Tag multi-select (loaded from API, optional)
- Save button → calls `linkweaveClient.createBookmark()`
- Success → shows `success-view.ts`
- Error → shows inline error message

#### `login-view.ts`
- Email + password form
- POST to `/api/j_security_check` with form-encoded body
- On success → transitions to `save-view.ts`
- On failure → shows error message

### 4.3 Extension Manifest (manifest.json)

```json
{
  "manifest_version": 3,
  "name": "LinkWeave Bookmark Saver",
  "version": "1.0.0",
  "description": "Save bookmarks to your LinkWeave collection",
  "permissions": [
    "activeTab",
    "cookies",
    "contextMenus",
    "storage"
  ],
  "host_permissions": [
    "https://linkweave.dev/*"
  ],
  "action": {
    "default_popup": "popup.html",
    "default_icon": {
      "16": "assets/icons/icon-16.png",
      "48": "assets/icons/icon-48.png",
      "128": "assets/icons/icon-128.png"
    }
  },
  "background": {
    "service_worker": "service-worker.js",
    "type": "module"
  },
  "icons": {
    "16": "assets/icons/icon-16.png",
    "48": "assets/icons/icon-48.png",
    "128": "assets/icons/icon-128.png"
  }
}
```

> **Note for local development:** Add a separate manifest for dev with `host_permissions`: `["https://local-linkweave.localhost:8443/*"]`. This can be managed via a Vite env variable that swaps the manifest during build.

---

## 5. Backend Changes

### 5.1 CORS Filter (New File)

**File:** `api/src/main/java/org/linkweave/infrastructure/cors/CorsFilter.java`

A JAX-RS `ContainerResponseFilter` that adds CORS headers to all API responses:

```
Responsibilities:
- Read Origin header from request
- If origin matches allowed patterns (linkweave domain, chrome-extension://, moz-extension://):
  - Set Access-Control-Allow-Origin to the request's Origin
  - Set Access-Control-Allow-Credentials: true
  - Set Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
  - Set Access-Control-Allow-Headers: Content-Type, Authorization
  - Set Access-Control-Max-Age: 3600
- Handle OPTIONS preflight requests by returning 200 with CORS headers
- Skip CORS for same-origin requests (no Origin header or same origin)
```

**Why a custom filter instead of Quarkus CORS config:**
- Quarkus has `quarkus.http.cors` configuration, but it doesn't support wildcard patterns for `chrome-extension://` origins
- A custom filter gives us full control over origin matching
- The filter is small (one class) and follows the existing `@Provider` pattern

### 5.2 CORS Origin Configuration

**File:** `api/src/main/resources/application.properties`

Add configuration for allowed CORS origins:

```properties
# CORS - Browser Extension
linkweave.cors.allowed-origins=https://linkweave.dev
%dev.linkweave.cors.allowed-origins=https://local-linkweave.localhost:8443
linkweave.cors.allow-extension-origins=true
```

### 5.3 Config Service Update

**File:** `api/src/main/java/org/linkweave/api/shared/config/ConfigService.java`

Add a method to read CORS configuration:

```java
public List<String> getCorsAllowedOrigins()
public boolean isCorsExtensionOriginsAllowed()
```

### 5.4 No New API Endpoints

The existing API is sufficient. The extension reuses:
- `POST /api/j_security_check` — login
- `GET /api/auth/me` — auth check + default collection
- `GET /api/collections/{id}` — collection with folders/tags (single request)
- `POST /api/bookmarks` — create bookmark

---

## 6. Data Flow

### 6.1 Login Flow

```
┌────────┐         ┌──────────┐         ┌────────────────┐
│  Popup │         │  Client  │         │  LinkWeave API  │
└───┬────┘         └────┬─────┘         └───────┬────────┘
    │   user opens popup   │                      │
    │─────────────────────→│                      │
    │   checkAuth()        │                      │
    │                      │  GET /api/auth/me    │
    │                      │─────────────────────→│
    │                      │  401 Unauthorized    │
    │                      │←─────────────────────│
    │   show login form    │                      │
    │←─────────────────────│                      │
    │                      │                      │
    │   submit credentials │                      │
    │─────────────────────→│                      │
    │                      │  POST /j_security_check
    │                      │  (form-encoded)      │
    │                      │─────────────────────→│
    │                      │  200 + Set-Cookie    │
    │                      │←─────────────────────│
    │                      │                      │
    │                      │  GET /api/auth/me    │
    │                      │─────────────────────→│
    │                      │  200 UserInfoJson    │
    │                      │←─────────────────────│
    │   show save form     │                      │
    │←─────────────────────│                      │
```

### 6.2 Save Bookmark Flow

```
┌────────┐         ┌──────────┐         ┌────────────────┐
│  Popup │         │  Client  │         │  LinkWeave API  │
└───┬────┘         └────┬─────┘         └───────┬────────┘
    │  popup opens with tab URL/title    │               │
    │─────────────────────→│              │               │
    │                      │              │               │
    │                      │  GET /api/auth/me            │
    │                      │─────────────→│               │
    │                      │  200 + defaultCollectionId   │
    │                      │←─────────────│               │
    │                      │              │               │
    │                      │  GET /api/collections/{id}   │
    │                      │─────────────→│               │
    │                      │  CollectionInfoJson          │
    │                      │  (folders, tags)             │
    │                      │←─────────────│               │
    │  render form with    │              │               │
    │  folders + tags      │              │               │
    │←─────────────────────│              │               │
    │                      │              │               │
    │  user clicks Save    │              │               │
    │─────────────────────→│              │               │
    │                      │  POST /api/bookmarks         │
    │                      │  { collectionId, url,        │
    │                      │    title, folderId, tagIds } │
    │                      │─────────────→│               │
    │                      │  200 BookmarkJson            │
    │                      │←─────────────│               │
    │  show success        │              │               │
    │←─────────────────────│              │               │
```

### 6.3 Context Menu Flow

```
┌──────────┐      ┌────────────────┐      ┌────────┐
│  Browser │      │ Service Worker │      │  Popup │
└────┬─────┘      └──────┬─────────┘      └───┬────┘
     │  right-click on page                   │
     │───────────────────→│                   │
     │  contextMenu click  │                   │
     │───────────────────→│                   │
     │                    │  open popup with   │
     │                    │  URL/title params  │
     │                    │───────────────────→│
     │                    │                   │
     │                    │  (normal save flow from here)│
```

---

## 7. API Client Design

### 7.1 Type Definitions (`api/types.ts`)

Mirrors the existing backend DTOs. These are kept as plain TypeScript interfaces — no code generation needed for the few types the extension uses:

```typescript
// Matches BookmarkSaveJson.java
interface BookmarkSaveJson {
  collectionId: string
  folderId?: string
  title: string
  url: string
  description?: string
  tagIds?: string[]
}

// Matches UserInfoJson.java
interface UserInfoJson {
  email: string
  firstName: string
  lastName: string
  roles: string[]
  defaultCollectionId: string
}

// Matches CollectionInfoJson.java
interface CollectionInfoJson {
  id: string
  name: string
  bookmarks: BookmarkJson[]
  tags: TagJson[]
  folders: FolderJson[]
}

// Matches TagJson.java
interface TagJson {
  id: string
  data: { collectionId: string; name: string; color: string }
}

// Matches FolderJson.java
interface FolderJson {
  id: string
  data: { collectionId: string; parentId?: string; name: string }
}
```

### 7.2 Client Implementation (`api/linkweave-client.ts`)

```typescript
class LinkWeaveClient {
  private baseUrl: string

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
  }

  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    })
    if (!response.ok) {
      throw new LinkWeaveApiError(response.status, await response.text())
    }
    return response.json()
  }

  // Auth
  async login(email: string, password: string): Promise<void> {
    const formData = new URLSearchParams()
    formData.append('j_username', email)
    formData.append('j_password', password)
    // Form POST — NOT JSON
    const response = await fetch(`${this.baseUrl}/j_security_check`, {
      method: 'POST',
      body: formData,
      credentials: 'include',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    if (response.status !== 200) throw new LinkWeaveApiError(401, 'Login failed')
  }

  async getCurrentUser(): Promise<UserInfoJson> { ... }
  async getCollection(id: string): Promise<CollectionInfoJson> { ... }
  async createBookmark(data: BookmarkSaveJson): Promise<BookmarkJson> { ... }
}
```

### 7.3 Base URL Configuration

The extension needs to know where the LinkWeave API is running. This is configured at build time via Vite environment variables:

```typescript
// src/api/config.ts
const API_BASE_URL = import.meta.env.VITE_LINKWEAVE_API_URL
  || 'https://linkweave.dev/api'

export const apiClient = new LinkWeaveClient(API_BASE_URL)
```

For local development:
```bash
VITE_LINKWEAVE_API_URL=https://local-linkweave.localhost:8443/api npm run dev
```

---

## 8. Build System

### 8.1 Vite Configuration

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import { resolve } from 'path'

export default defineConfig({
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'src/popup/popup.html'),
        'service-worker': resolve(__dirname, 'src/background/service-worker.ts'),
      },
      output: {
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
    target: 'chrome120', // MV3 target
  },
})
```

### 8.2 Scripts (package.json)

```json
{
  "name": "linkweave-extension",
  "scripts": {
    "dev": "vite build --watch --mode development",
    "build": "vite build",
    "type-check": "tsc --noEmit"
  },
  "devDependencies": {
    "typescript": "~5.9.3",
    "vite": "^7.3.1",
    "@types/chrome": "^0.0.287"
  }
}
```

### 8.3 Build Output Structure

```
dist/
├── popup.html
├── popup.js
├── popup.css
├── service-worker.js
├── manifest.json              # Copied from root, with env substitution
└── assets/
    └── icons/
        ├── icon-16.png
        ├── icon-48.png
        └── icon-128.png
```

---

## 9. Popup UI Design

### 9.1 Layout (Approximate)

```
┌─────────────────────────────────┐
│ 🔗 Add to LinkWeave      [⚙️]  │
├─────────────────────────────────┤
│                                 │
│  URL ─────────────────────────  │
│  https://github.com/repo/issue  │
│  [📎 Full URL] [🌐 Domain only] │
│                                 │
│  Title ──────────────────────   │
│  Fix: Bug in authentication     │
│                                 │
│  Collection ──────────────────  │
│  [My Bookmarks          ▼]     │
│                                 │
│  Folder ─────────────────────   │
│  [No folder             ▼]     │
│                                 │
│  Tags                           │
│  [bug] [backend] [urgent]      │
│  (+ click to toggle)           │
│                                 │
│  ┌───────────────────────────┐  │
│  │        Save Bookmark      │  │
│  └───────────────────────────┘  │
│                                 │
└─────────────────────────────────┘
```

### 9.2 Login View

```
┌─────────────────────────────────┐
│ 🔗 LinkWeave                    │
├─────────────────────────────────┤
│                                 │
│  Email                          │
│  [user@example.com        ]    │
│                                 │
│  Password                       │
│  [••••••••                ]    │
│                                 │
│  ┌───────────────────────────┐  │
│  │          Login            │  │
│  └───────────────────────────┘  │
│                                 │
│  ⚠️ Invalid credentials         │
│                                 │
└─────────────────────────────────┘
```

### 9.3 Success View

```
┌─────────────────────────────────┐
│ 🔗 LinkWeave                    │
├─────────────────────────────────┤
│                                 │
│         ✅ Saved!               │
│                                 │
│  "Fix: Bug in authentication"   │
│  added to My Bookmarks          │
│                                 │
│  ┌───────────────────────────┐  │
│  │       Add Another         │  │
│  └───────────────────────────┘  │
│                                 │
└─────────────────────────────────┘
```

---

## 10. Implementation Map

### Phase 1: Backend — CORS Support

| # | File | Action | Description |
|---|------|--------|-------------|
| 1.1 | `api/src/main/java/org/linkweave/infrastructure/cors/CorsFilter.java` | **Create** | JAX-RS `ContainerResponseFilter` that adds CORS headers. Matches origins against allowed patterns (LinkWeave domain, `chrome-extension://*`, `moz-extension://*`). Handles OPTIONS preflight. |
| 1.2 | `api/src/main/resources/application.properties` | **Modify** | Add `linkweave.cors.allowed-origins` config property (dev + prod). |
| 1.3 | `api/src/main/java/org/linkweave/api/shared/config/ConfigService.java` | **Modify** | Add `getCorsAllowedOrigins()` and `isCorsExtensionOriginsAllowed()` methods. |
| 1.4 | `CorsFilter` | **Test** | Unit test: verify CORS headers for allowed origins, no headers for disallowed origins, OPTIONS preflight handling. |

### Phase 2: Extension — Project Scaffold

| # | File | Action | Description |
|---|------|--------|-------------|
| 2.1 | `extension/` | **Create** | Create directory at project root. |
| 2.2 | `extension/package.json` | **Create** | Extension package with deps: `typescript`, `vite`, `@types/chrome`. |
| 2.3 | `extension/tsconfig.json` | **Create** | TypeScript config targeting ES2022, module ESNext. |
| 2.4 | `extension/vite.config.ts` | **Create** | Vite config with multi-entry build for popup + service worker. |
| 2.5 | `extension/manifest.json` | **Create** | MV3 manifest with permissions, host_permissions, popup, background. |
| 2.6 | `extension/.env` | **Create** | Default env vars: `VITE_LINKWEAVE_API_URL=https://linkweave.dev/api`. |
| 2.7 | `extension/.env.development` | **Create** | Dev env vars: `VITE_LINKWEAVE_API_URL=https://local-linkweave.localhost:8443/api`. |
| 2.8 | `extension/assets/icons/` | **Create** | Extension icons (16, 48, 128px). Can use the existing `frontend/src/assets/logo.svg` as a starting point. |

### Phase 3: Extension — Core Modules

| # | File | Action | Description |
|---|------|--------|-------------|
| 3.1 | `extension/src/api/types.ts` | **Create** | TypeScript interfaces mirroring backend DTOs. |
| 3.2 | `extension/src/api/config.ts` | **Create** | Base URL configuration from env vars. |
| 3.3 | `extension/src/api/linkweave-client.ts` | **Create** | HTTP client with `login()`, `getCurrentUser()`, `getCollection()`, `createBookmark()`. All requests use `credentials: 'include'`. |
| 3.4 | `extension/src/api/errors.ts` | **Create** | `LinkWeaveApiError` class with status code and message. |
| 3.5 | `extension/src/auth/auth-service.ts` | **Create** | Auth state management: `checkAuth()`, `login()`, `logout()`, `isAuthenticated`, `user` state. |
| 3.6 | `extension/src/storage/extension-storage.ts` | **Create** | `chrome.storage.local` helpers for caching collection data (folders, tags) and settings. |
| 3.7 | `extension/src/lib/dom.ts` | **Create** | Minimal DOM helpers: `el()`, `setText()`, `setClass()`, `on()`. |

### Phase 4: Extension — Popup UI

| # | File | Action | Description |
|---|------|--------|-------------|
| 4.1 | `extension/src/popup/popup.html` | **Create** | Minimal HTML shell with `<div id="app">`. |
| 4.2 | `extension/src/popup/popup.css` | **Create** | Clean, minimal styles. Consistent with LinkWeave brand. |
| 4.3 | `extension/src/popup/popup.ts` | **Create** | Entry point: checks auth, reads active tab, renders appropriate view. |
| 4.4 | `extension/src/popup/views/login-view.ts` | **Create** | Login form with email/password. Calls `authService.login()`. On success → transition to save view. |
| 4.5 | `extension/src/popup/views/save-view.ts` | **Create** | Main bookmark save form: URL (with full/domain toggle), title, collection selector, folder dropdown, tag multi-select, save button. |
| 4.6 | `extension/src/popup/views/success-view.ts` | **Create** | Success confirmation with "Add Another" button. |
| 4.7 | `extension/src/popup/components/folder-select.ts` | **Create** | Renders folder tree as a flat `<select>` with indentation. |
| 4.8 | `extension/src/popup/components/tag-select.ts` | **Create** | Renders tags as toggle-able pills with colors. |

### Phase 5: Extension — Background Service Worker

| # | File | Action | Description |
|---|------|--------|-------------|
| 5.1 | `extension/src/background/service-worker.ts` | **Create** | Registers context menu "Add to LinkWeave". On click → opens popup with URL pre-filled via `chrome.action.openPopup()` or message passing. |

### Phase 6: Testing & Polish

| # | File | Action | Description |
|---|------|--------|-------------|
| 6.1 | `extension/src/api/linkweave-client.test.ts` | **Create** | Unit tests for API client (mock fetch). |
| 6.2 | `extension/src/auth/auth-service.test.ts` | **Create** | Unit tests for auth state management. |
| 6.3 | Manual testing | — | Load extension in Chrome/Firefox dev mode. Test login, save, context menu. |
| 6.4 | `extension/README.md` | **Create** | Build instructions, development setup, how to load unpacked extension. |

---

## 11. Build Sequence (Checklist)

### Backend (API) Changes
- [ ] Create `CorsFilter.java` — JAX-RS response filter
- [ ] Add CORS config properties to `application.properties`
- [ ] Update `ConfigService.java` to read CORS config
- [ ] Test CORS filter with unit tests
- [ ] Verify CORS headers in dev mode (`quarkus:dev`)
- [ ] Verify existing web app still works (no regressions)

### Extension Scaffold
- [ ] Create `extension/` directory
- [ ] Create `package.json`, `tsconfig.json`, `vite.config.ts`
- [ ] Create `manifest.json` with permissions
- [ ] Create `.env` and `.env.development` files
- [ ] Add extension icons
- [ ] Verify `npm run build` produces valid loadable extension

### Extension Core
- [ ] Implement `api/types.ts` — DTO interfaces
- [ ] Implement `api/config.ts` — base URL config
- [ ] Implement `api/errors.ts` — error types
- [ ] Implement `api/linkweave-client.ts` — HTTP client
- [ ] Implement `auth/auth-service.ts` — auth state
- [ ] Implement `storage/extension-storage.ts` — storage helpers
- [ ] Implement `lib/dom.ts` — DOM utilities

### Extension UI
- [ ] Create `popup.html` and `popup.css`
- [ ] Implement `popup.ts` — entry point with auth routing
- [ ] Implement `login-view.ts` — login form
- [ ] Implement `save-view.ts` — bookmark save form
- [ ] Implement `success-view.ts` — success confirmation
- [ ] Implement `folder-select.ts` — folder tree dropdown
- [ ] Implement `tag-select.ts` — tag pill toggle

### Extension Background
- [ ] Implement `service-worker.ts` — context menu registration
- [ ] Wire context menu click to popup

### Testing
- [ ] Write unit tests for `linkweave-client.ts`
- [ ] Write unit tests for `auth-service.ts`
- [ ] Manual test: login flow
- [ ] Manual test: save bookmark with all fields
- [ ] Manual test: save bookmark with domain-only
- [ ] Manual test: context menu → save
- [ ] Manual test: session expiry → re-login
- [ ] Manual test: Firefox compatibility

---

## 12. Security Considerations

| Concern | Mitigation |
|---------|------------|
| **Session cookie exposure** | Cookie is HttpOnly and encrypted (Quarkus form auth). The extension never reads the cookie directly — the browser sends it via `credentials: 'include'`. |
| **CSRF** | Browser extensions are not susceptible to traditional CSRF because they don't share the same origin as the web app. The CORS filter validates the Origin header. |
| **CORS origin spoofing** | The CORS filter explicitly checks for known patterns: the LinkWeave domain and `chrome-extension://`/`moz-extension://` prefixes. It doesn't use `*`. |
| **Extension permissions** | The manifest requests minimal permissions: `activeTab` (read current tab URL/title), `cookies` (read cookie for auth check), `contextMenus` (right-click menu), `storage` (cache settings). |
| **API data exposure** | The extension only calls existing authenticated endpoints. `AuthorizationService` enforces collection access on every call. No new attack surface. |
| **Stored credentials** | The extension does NOT store passwords. Auth is session-based. The session cookie is managed by the browser. |
| **Content Security Policy** | The popup HTML uses no inline scripts (Vite bundles them). The manifest can set a strict CSP if needed. |

---

## 13. Performance Considerations

| Concern | Approach |
|---------|----------|
| **Popup load time** | Minimal JS bundle (< 20KB). No framework. No generated code. CSS is inline. |
| **API call count** | Exactly 2 API calls on popup open: `GET /auth/me` + `GET /collections/{id}`. One more for save: `POST /bookmarks`. |
| **Caching** | Folder and tag lists are cached in `chrome.storage.session` (valid for the browser session). Avoids re-fetching on every popup open. Cache is invalidated on successful save. |
| **Bundle size** | No framework. Only `@types/chrome` (dev only). Estimated bundle: < 15KB gzipped. |

---

## 14. Dev/Prod Configuration

### Development
- Extension connects to `https://local-linkweave.localhost:8443/api`
- Backend runs via `./mvnw quarkus:dev` with local SSL certs
- Extension loaded as unpacked in Chrome developer mode
- CORS allows `chrome-extension://*` origins

### Production
- Extension connects to `https://linkweave.dev/api`
- Extension packaged as `.zip` for Chrome Web Store / Firefox Add-ons
- CORS allows `chrome-extension://*` and `moz-extension://*` origins
- `host_permissions` point to production domain

### Environment Switching
The extension uses Vite env variables to configure the API base URL at build time:

```bash
# Development build
npm run build  # uses .env.development → local-linkweave.localhost

# Production build  
npm run build -- --mode production  # uses .env → linkweave.dev
```

---

## 15. Future Considerations

| Feature | Notes |
|---------|-------|
| **API Tokens** | Add personal access token support so users can generate long-lived tokens from the web UI. The extension could store the token instead of relying on sessions. This also enables CLI/headless use. |
| **Multiple Collections** | Currently the popup defaults to the user's default collection. Could add a collection selector for users with multiple collections. |
| **Quick Save (No UI)** | Keyboard shortcut that saves to the default collection + last-used folder without opening the popup. Uses `chrome.commands` API. |
| **Page Metadata** | Content script that extracts `<meta description>`, OG tags, and favicon for richer bookmark data. |
| **Duplicate Detection** | Check if the URL already exists in the collection before saving. Show a warning if duplicate found. |
| **Firefox Support** | Same codebase with minor manifest differences. Use a build script to generate Firefox-specific manifest (`browser_specific_settings`). |
