import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { apiFetch, clearToken, getToken, setToken } from '../lib/api'

interface LoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
}

interface AuthContextValue {
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => getToken() !== null)

  useEffect(() => {
    function handleUnauthorized() {
      setIsAuthenticated(false)
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
  }

  function logout() {
    clearToken()
    setIsAuthenticated(false)
  }

  return <AuthContext.Provider value={{ isAuthenticated, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
