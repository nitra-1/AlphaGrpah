const TOKEN_KEY = 'alphagraph_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/**
 * Every request goes through here - attaches the JWT if present, and on a 401 clears the token
 * so ProtectedRoute redirects to /login on the next render. Tokens are short-lived (1h) with no
 * refresh flow (docs/004_API_Architecture.md §4) - re-login on expiry is the only path, by design.
 */
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`/api/v1${path}`, { ...options, headers })

  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event('alphagraph:unauthorized'))
    throw new ApiError(401, 'Session expired - please log in again')
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, body?.message ?? `Request failed (${response.status})`)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}
