import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/** Guards the four ADMIN-only pages (News Review, Add Instrument, Add Financial Data, Add User) - a USER hitting one of these URLs directly is redirected rather than shown a form that would just 403 on submit. Nested inside ProtectedRoute, so isAuthenticated is already guaranteed here. */
export function AdminRoute() {
  const { role } = useAuth()
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
