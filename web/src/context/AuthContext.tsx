import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { apiFetch, clearToken, getToken, setToken } from '../lib/api'

interface LoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
}

interface AuthContextValue {
  isAuthenticated: boolean
  role: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Reads the `role` claim straight out of the JWT payload - no signature check, since this is
 * UI-only convenience (which nav links to show) and every actual permission is still enforced
 * server-side via @PreAuthorize. Never trust this for anything security-relevant.
 */
function decodeRole(token: string): string | null {
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    )
    return JSON.parse(json).role ?? null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => getToken() !== null)
  const [role, setRole] = useState<string | null>(() => {
    const token = getToken()
    return token ? decodeRole(token) : null
  })

  useEffect(() => {
    function handleUnauthorized() {
      setIsAuthenticated(false)
      setRole(null)
    }
    window.addEventListener('alphagraph:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('alphagraph:unauthorized', handleUnauthorized)
  }, [])

  async function login(email: string, password: string) {
    const response = await apiFetch<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setToken(response.token)
    setIsAuthenticated(true)
    setRole(decodeRole(response.token))
  }

  function logout() {
    clearToken()
    setIsAuthenticated(false)
    setRole(null)
  }

  return <AuthContext.Provider value={{ isAuthenticated, role, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
