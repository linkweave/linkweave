const API_BASE = '/api'

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers
    }
  })

  if (response.status === 401) {
    throw new Error('Unauthorized')
  }

  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

export interface UserInfo {
  email: string
  firstName: string
  lastName: string
  roles: string[]
}

export const api = {
  get: <T>(path: string) => fetchApi<T>(path),
  post: <T>(path: string, data?: unknown) =>
    fetchApi<T>(path, { method: 'POST', body: data ? JSON.stringify(data) : undefined }),
  put: <T>(path: string, data: unknown) =>
    fetchApi<T>(path, { method: 'PUT', body: JSON.stringify(data) }),
  delete: <T>(path: string) => fetchApi<T>(path, { method: 'DELETE' })
}
