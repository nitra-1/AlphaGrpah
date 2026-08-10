import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { PlatformUserSummary } from '../types/user'
import { ErrorState } from '../components/ErrorState'

export function AddUserPage() {
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<'USER' | 'ADMIN'>('USER')
  const [lastCreated, setLastCreated] = useState<PlatformUserSummary | null>(null)

  const usersQuery = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => apiFetch<PlatformUserSummary[]>('/admin/users'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiFetch<PlatformUserSummary>('/admin/users', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), password, role }),
      }),
    onSuccess: (created) => {
      setLastCreated(created)
      setEmail('')
      setPassword('')
      setRole('USER')
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const canSubmit = email.trim().length > 0 && password.length >= 8 && !createMutation.isPending

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold text-text">Add User</h1>
      <p className="mt-1 text-sm text-text-muted">
        There is no public sign-up - this is the only way a new account gets created. Each account gets its own private
        Portfolio, Watchlist, and Trade Journal; the ADMIN role only gates the admin-only pages (News Review, Add Instrument,
        Add Financial Data), never portfolio/watchlist access.
      </p>

      {lastCreated && (
        <div className="mt-6 rounded-2xl border border-accent-soft bg-accent-soft p-4 text-sm text-text">
          <span className="font-semibold">{lastCreated.email}</span> was created as {lastCreated.role}.
        </div>
      )}

      <div className="mt-6 space-y-5 rounded-2xl border border-border bg-surface p-6">
        <div>
          <label className="block text-sm font-medium text-text">Email</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="name@example.com"
            className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-text">Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="At least 8 characters"
            className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-text">Role</label>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as 'USER' | 'ADMIN')}
            className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
          >
            <option value="USER">USER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>

        {createMutation.error && <ErrorState message={(createMutation.error as Error).message} onRetry={() => createMutation.mutate()} />}

        <button
          onClick={() => createMutation.mutate()}
          disabled={!canSubmit}
          className="w-full rounded-lg bg-accent px-4 py-2.5 text-sm font-semibold text-white hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
        >
          {createMutation.isPending ? 'Creating…' : 'Create User'}
        </button>
      </div>

      <div className="mt-8">
        <h2 className="text-sm font-semibold text-text">Existing accounts</h2>
        {usersQuery.isLoading && <p className="mt-2 text-sm text-text-muted">Loading…</p>}
        {usersQuery.error && <ErrorState message={(usersQuery.error as Error).message} onRetry={() => usersQuery.refetch()} />}
        {usersQuery.data && (
          <div className="mt-2 overflow-hidden rounded-2xl border border-border">
            {usersQuery.data.map((u) => (
              <div key={u.id} className="flex items-center justify-between border-b border-border px-4 py-2.5 text-sm last:border-b-0">
                <span className="text-text">{u.email}</span>
                <span className="text-text-muted">
                  {u.role} {u.active ? '' : '· suspended'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
