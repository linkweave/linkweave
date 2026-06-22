# UI Layout Specification

**Project:** LinkWeave Bookmark Manager
**Source:** [requirements.md](requirements.md), [use_cases/](use_cases/)
**Date:** 2026-03-27

---

## Overview

This document defines the user interface layout and design system for LinkWeave.
It serves as a reference for frontend implementation using Vue.js with Tailwind CSS.

---

## Main Application Layout

### Desktop Layout (≥1024px)

Three-column responsive layout:

```
┌─────────────────────────────────────────────────────────────┐
│  Header: Collection Selector | Search | User Menu | + Add  │
├────────────┬────────────────────────────────────────────────┤
│            │                                                │
│  Sidebar   │              Main Content                      │
│  (240px)   │                                                │
│            │   ┌─────────────────────────────────────────┐  │
│  ┌──────┐  │   │  Breadcrumb: All / Folder / Subfolder   │  │
│  │Folder│  │   └─────────────────────────────────────────┘  │
│  │ Tree │  │                                                │
│  │      │  │   ┌──────────────┐  ┌──────────────┐          │
│  └──────┘  │   │ [icon] Title │  │ [icon] Title │          │
│            │   │       URL    │  │       URL    │          │
│  ┌──────┐  │   │ [tag] [tag]  │  │ [tag]        │          │
│  │ Tag  │  │   └──────────────┘  └──────────────┘          │
│  │Filter│  │                                                │
│  └──────┘  │   ┌──────────────┐  ┌──────────────┐          │
│            │   │ [icon] Title │  │ [icon] Title │          │
│            │   │       URL    │  │       URL    │          │
│            │   │ [tag]        │  │ [tag] [tag]  │          │
│            │   └──────────────┘  └──────────────┘          │
└────────────┴────────────────────────────────────────────────┘
```

### Mobile Layout (<1024px)

Sidebar collapses to a drawer overlay triggered by hamburger menu.

---

## Header Component

### Elements
| Element | Description |
|---------|-------------|
| Collection Selector | Always visible dropdown to switch between collections |
| Search Bar | Full-text search across bookmarks, tags, URLs |
| Add Bookmark Button | Primary action, opens create dialog |
| User Menu | Username button (shows display name) that opens a dropdown with language switcher ("DE \| EN", current underlined) and logout |

### Behavior
- Fixed at top of viewport
- User Menu only visible when authenticated
- Search with debounce (300ms)

---

## Sidebar Components

### Folder Tree

| Feature | Description |
|---------|-------------|
| Root node | "All Bookmarks" shows unfiltered list |
| Nesting | Folders support up to 3 levels deep |
| Expand/Collapse | Chevron icons (rotated 90° when expanded, invisible for leaf nodes); default expanded |
| Selection | Single-select, highlights active folder |
| Folder Icons | `FolderOpen` when expanded with children, `Folder` otherwise; both in `text-primary` |
| Actions | Hover "..." button (appears via `group-hover:opacity-100` pattern) opens dropdown: Create Subfolder, Rename, Delete (destructive red) |

### Tag Filter

| Feature | Description |
|---------|-------------|
| Tag List | All tags with color indicator and bookmark count |
| Tag Colors | Auto-assigned from predefined palette on creation; user-customizable |
| Multi-select | Click to toggle, shows AND filtered results |
| Clear All | Button to reset tag filters |
| Create Tag | Input field at bottom to add new tag |

---

## Main Content Area

### Bookmark List

| Feature | Description |
|---------|-------------|
| Layout | Two-column card grid on desktop, single column on mobile |
| Sorting | Default: newest first (by created_at desc) |
| Infinite Scroll | Load more bookmarks as user scrolls down |
| Empty State | Message + "Add first bookmark" CTA |

### Bookmark Card

| Element | Description |
|---------|-------------|
| Favicon | 40x40px, fallback to globe icon |
| Title | Bold, single line with ellipsis |
| URL | Muted text, single line with ellipsis |
| Tags | Badge chips with tag colors |
| Folder | "in FolderName" muted text |
| Actions | Hover "..." button opens dropdown: Edit, Move to Folder, Delete (destructive red) |

### Breadcrumb Navigation

Shows current folder path: `All Bookmarks / Work / Frontend / React`

- Displayed above the bookmark list when a folder is selected
- Each segment is a clickable button to navigate up the folder hierarchy
- Last segment (current folder) is bold (`font-medium`, `text-foreground`)
- Parent segments are muted (`text-muted-foreground`) with hover effect
- Separator: `/` character in muted color
- "All Bookmarks" root segment navigates to unfiltered view (sets `selectedFolderId` to `null`)
- Hidden when no folder is selected (All Bookmarks view)

---

## Dialogs

### Create/Edit Bookmark Dialog

| Field | Type | Required |
|-------|------|----------|
| URL | Text input | Yes |
| Title | Text input | Yes |
| Description | Textarea | No |
| Folder | Dropdown select | No |
| Tags | Multi-select with autocomplete | No |

- Auto-fetch title from URL (optional button)
- Create new tag inline if doesn't exist

### Create/Edit Folder Dialog

| Field | Type | Required |
|-------|------|----------|
| Name | Text input | Yes |

### Delete Confirmation Dialog

- Warning text for folder: "This will delete X bookmarks in this folder"
- Cancel / Delete buttons

---

## Design System

### Component Library
- **Base**: shadcn/vue (Vue port of shadcn/ui)
- **Styling**: Tailwind CSS 4.x
- **Icons**: Lucide Vue

### Color Tokens (CSS Variables)
| Token | Light Mode | Dark Mode |
|-------|------------|-----------|
| --background | #ffffff | #0a0a0a |
| --foreground | #171717 | #ededed |
| --card | #ffffff | #0a0a0a |
| --primary | #2563eb | #3b82f6 |
| --secondary | #f5f5f5 | #262626 |
| --muted | #f5f5f5 | #262626 |
| --accent | #f5f5f5 | #262626 |
| --destructive | #ef4444 | #dc2626 |
| --border | #e5e5e5 | #262626 |

### Typography
- **Font**: System font stack (Inter or Geist if loaded)
- **Heading**: font-semibold
- **Body**: font-normal
- **Small/Muted**: text-sm text-muted-foreground

### Spacing
- **Card padding**: 16px (p-4)
- **Section gap**: 24px (space-y-6)
- **Sidebar width**: 240px (w-60)

### Border Radius
- **Cards**: 8px (rounded-lg)
- **Buttons**: 6px (rounded-md)
- **Inputs**: 6px (rounded-md)
- **Tags/Badges**: 9999px (rounded-full)

---

## Responsive Breakpoints

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| Mobile | < 640px | Single column cards, drawer sidebar |
| Tablet | 640px - 1023px | Two-column cards, collapsed sidebar |
| Desktop | ≥ 1024px | Two-column cards, full sidebar visible |

---

## Loading States

### Skeleton Loading
- Bookmark cards show skeleton while fetching
- Folder tree shows skeleton on initial load

### Optimistic Updates
- Create/Edit/Delete actions update UI immediately
- Revert on API error with toast notification

---

## Error Handling

### Toast Notifications
- Success: Green, auto-dismiss 3s
- Error: Red, manual dismiss or 5s
- Warning: Yellow, manual dismiss

### Form Validation
- Inline error messages below fields
- Submit button disabled until valid

---

## Accessibility

- All interactive elements keyboard navigable
- Focus indicators visible
- ARIA labels for icons
- Screen reader announcements for dynamic content

---

## State Management

### Pinia Stores

| Store | Responsibility |
|-------|----------------|
| `useCollectionStore` | Current collection, collection list, default collection |
| `useBookmarkStore` | Bookmarks CRUD, filtering, search, infinite scroll |
| `useFolderStore` | Folder tree, CRUD operations |
| `useTagStore` | Tags CRUD, tag filters |
| `useUiStore` | Sidebar state, dialog state, theme |

---

## API Integration

REST API endpoints are defined in the [use case specifications](use_cases/).
See individual use cases for endpoint details and request/response formats.

---

## Future Considerations

- Bulk actions (select multiple, delete, move)
- Export/import bookmarks
- Keyboard shortcuts
- PWA support for offline access
