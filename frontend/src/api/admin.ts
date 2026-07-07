/**
 * Thin client for the /api/admin endpoints. Lives outside the OpenAPI-generated
 * code so the admin UI doesn't have to wait for `pnpm run generate-api` to
 * pick up new operations. Mirrors the manual-fetch pattern already used by
 * the form-login and click-tracking flows.
 */

import type { Permission } from '@/api/generated'

export type AdminAuthProvider = 'FORM' | 'OIDC' | null

export interface AdminUser {
  id: string
  email: string
  firstName: string
  lastName: string
  active: boolean
  authProvider: AdminAuthProvider
  roles: Permission[]
}

export interface PasswordResetResult {
  newPassword: string
}

const JSON_HEADERS = { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' } as const

export async function fetchAllUsers(): Promise<AdminUser[]> {
  const response = await fetch('/api/admin/users', {
    method: 'GET',
    credentials: 'include',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`Failed to load users (HTTP ${response.status})`)
  }
  const body = (await response.json()) as { users: AdminUser[] }
  return body.users ?? []
}

export async function resetUserPassword(userId: string): Promise<PasswordResetResult> {
  const response = await fetch(`/api/admin/users/${encodeURIComponent(userId)}/reset-password`, {
    method: 'POST',
    credentials: 'include',
    headers: JSON_HEADERS,
  })
  if (!response.ok) {
    throw new Error(`Failed to reset password (HTTP ${response.status})`)
  }
  return (await response.json()) as PasswordResetResult
}
