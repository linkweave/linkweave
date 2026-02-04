Bookmark Manager – Technical Specification (Agent Version)
1. Purpose

Build a self-hosted bookmark management web application with:
Central server as the sole source of truth
Access from multiple devices via browser
Server-rendered HTML using Qute templates
htmx for UI interactions
SQLite for persistence
Session-based authentication

Read-only offline fallback using a Service Worker

Offline behavior is explicitly read-only.
Quarkus currently is configured to use a self signed certificate and is listening on
https://local-chainlink.localhost:8443/


2. Architecture Overview
Browser
├─ HTML (Qute templates)
├─ htmx
├─ Service Worker
│  └─ Cached HTML responses
│
└── HTTP (session cookie)
     ↓
Quarkus Application Server
├─ Session Auth
├─ HTML Controllers
├─ SQLite Database
└─ Static Assets

3. Authentication & Sessions
Authentication Model
Quarkus already supports Form-based login (https://quarkus.io/guides/security-authentication-mechanisms#form-auth)
Session cookie issued by Quarkus



Flow

GET /login → login form

POST /login → validate credentials

On success:

Quarkus creatres server-side session

Issue session cookie

All other routes require authentication

Notes

Single user for PoC

Credentials stored in config or DB

Designed so OIDC can replace this later

4. Persistence (SQLite)
Storage

SQLite file on disk

Managed via Flyway

One database per server instance

Path:

developer-local-settings/chainlink.db

5. Domain Model
Entitis should extend AbstractEntity for id etc

Bookmark
- title (string)
- url (URL)
- tags (ManyToMany relation to Tag)
- notes (string)
- folder (ManyToOne relation to Folder)

Tag
- name	TEXT (unique)

Folder
- parent (ManyToOne relation to Folder, nullable)
- name

6. Server Responsibilities

The server:

Is the only place where data is modified
Renders all HTML
Enforces authentication
Returns complete pages or fragments. To that end it uses Quarkus with the Qute template and htmx.
Where sensible you can create REST resources and inject the template to generate it
Never depends on client-side state

7. HTTP Endpoints (HTML)
Authentication
GET  /login
POST /login
POST /logout

Pages
GET /               → main bookmark list
GET /bookmarks      → filtered list
GET /bookmarks/new  → create form
GET /bookmarks/{id} → detail view

Mutations (htmx)
POST   /bookmarks
PUT    /bookmarks/{id}
DELETE /bookmarks/{id}

Behavior

Mutations require active session

On success:

Persist data

Return HTML fragment

On failure:

Return error fragment

Offline mutations are not supported

8. htmx Usage Rules

htmx used for:

Form submissions

Partial updates

Server always returns HTML

No JSON APIs for core flows

Example:

<form hx-post="/bookmarks" hx-target="#bookmark-list">

9. Offline Read-Only Support
Design Goal

When offline:

App still loads

Last-seen bookmark pages are viewable

All mutations fail gracefully

10. Service Worker Responsibilities
Scope

Cache HTML responses

Cache static assets (CSS, JS)

Intercept htmx requests

Serve cached HTML when offline

Caching Strategy

Network-first

Fallback to cache on failure

What Is Cached

GET /

GET /bookmarks

GET /bookmarks/{id}

Static assets

What Is NOT Cached

Login responses

POST/PUT/DELETE

User credentials

11. Offline Behavior (Explicit)
When Offline

GET requests:

Served from cache if available

htmx:

Receives cached HTML fragments

Forms:

Submissions fail

UI shows “Offline – read-only mode”

When Online Again

Normal server behavior resumes

Cached content updated automatically

12. Service Worker Implementation Rules

Plain JavaScript

No framework

One service worker file

No background sync

No IndexedDB required

Pseudo-logic:

fetch event:
  if GET:
    try network
    cache response
    return response
  if network fails:
    return cached response

13. JavaScript Constraints

JavaScript must:

Register service worker

Display offline indicator

JavaScript must not:

Render content

Own state

Modify data

Replace server logic

14. Security Considerations

Session cookie must not be cached

Cached pages assumed safe for the logged-in user

Logout should clear service worker caches (best effort)

15. Non-Goals

No offline edits

No conflict resolution

No SPA

No real-time sync

No mobile app

16. Implementation Order

Bookmark CRUD (server only)

Session authentication

htmx interactions

SQLite + Flyway

Service worker (read-only cache)

Offline indicator

Cache invalidation on logout

17. Definition of Done (PoC)

Bookmarks accessible from multiple devices

Server remains authoritative

App usable without JS (online)

App readable offline (cached HTML)

All mutations blocked while offline

SQLite is the only persistent datastore

Summary for Coding Agent

This is a server-rendered, session-based, HTML-first application.
The server is authoritative.
The browser may cache HTML for read-only offline access.
No client-side state or offline writes are permitted.
