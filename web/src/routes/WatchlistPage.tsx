import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { WatchlistEntry } from '../types/watchlist'
import type { RankingEntry } from '../types/rankings'
import { RatingBadge } from '../components/RatingBadge'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function WatchlistPage() {
  const queryClient = useQueryClient()
  const [selectedInstrumentId, setSelectedInstrumentId] = useState('')

  const watchlistQuery = useQuery({
    queryKey: ['watchlist'],
    queryFn: () => apiFetch<WatchlistEntry[]>('/watchlist'),
  })

  const rankingsQuery = useQuery({
    queryKey: ['rankings'],
    queryFn: () => apiFetch<RankingEntry[]>('/rankings'),
  })

  const addMutation = useMutation({
    mutationFn: (instrumentId: string) =>
      apiFetch<WatchlistEntry>('/watchlist', { method: 'POST', body: JSON.stringify({ instrumentId }) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['watchlist'] })
      setSelectedInstrumentId('')
    },
  })

  const removeMutation = useMutation({
    mutationFn: (instrumentId: string) => apiFetch<void>(`/watchlist/${instrumentId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['watchlist'] }),
  })

  const watchedIds = new Set((watchlistQuery.data ?? []).map((entry) => entry.instrumentId))
  const availableToAdd = (rankingsQuery.data ?? []).filter((entry) => !watchedIds.has(entry.instrumentId))

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Watchlist</h1>
      <p className="mt-1 text-sm text-text-muted">
        The shared watchlist, with each instrument's current Swing/Long-Term Score and Rank (Module 3.2).
      </p>

      <div className="mt-6 flex items-center gap-3">
        <select
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
          value={selectedInstrumentId}
          onChange={(e) => setSelectedInstrumentId(e.target.value)}
        >
          <option value="">Add an instrument…</option>
          {availableToAdd.map((entry) => (
            <option key={entry.instrumentId} value={entry.instrumentId}>
              {entry.symbol}
            </option>
          ))}
        </select>
        <button
          className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
          disabled={!selectedInstrumentId || addMutation.isPending}
          onClick={() => addMutation.mutate(selectedInstrumentId)}
        >
          Add
        </button>
        {addMutation.isError && <span className="text-sm text-loss">Couldn't add that instrument.</span>}
      </div>

      {watchlistQuery.isLoading && <p className="mt-8 text-sm text-text-muted">Loading…</p>}
      {watchlistQuery.error && <p className="mt-8 text-sm text-loss">Couldn't load the watchlist.</p>}

      {watchlistQuery.data && watchlistQuery.data.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">Nothing on the watchlist yet - add an instrument above.</p>
      )}

      {watchlistQuery.data && watchlistQuery.data.length > 0 && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Added</th>
                <th className="px-4 py-3 font-medium">Swing Score</th>
                <th className="px-4 py-3 font-medium">Rating</th>
                <th className="px-4 py-3 font-medium">Swing Rank</th>
                <th className="px-4 py-3 font-medium">Long-Term Score</th>
                <th className="px-4 py-3 font-medium">Rating</th>
                <th className="px-4 py-3 font-medium">Long-Term Rank</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {watchlistQuery.data.map((entry) => (
                <tr key={entry.instrumentId} className="border-b border-border last:border-0 hover:bg-bg">
                  <td className="px-4 py-3 font-semibold text-text">{entry.symbol}</td>
                  <td className="px-4 py-3 text-text-muted">{formatDate(entry.addedAt)}</td>
                  <td className="px-4 py-3 text-text">{entry.swingScore?.toFixed(2) ?? '—'}</td>
                  <td className="px-4 py-3"><RatingBadge rating={entry.swingRating} /></td>
                  <td className="px-4 py-3 text-text">{entry.swingRank ?? '—'}</td>
                  <td className="px-4 py-3 text-text">{entry.longTermScore?.toFixed(2) ?? '—'}</td>
                  <td className="px-4 py-3"><RatingBadge rating={entry.longTermRating} /></td>
                  <td className="px-4 py-3 text-text">{entry.longTermRank ?? '—'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      className="text-xs font-semibold text-loss hover:underline disabled:opacity-50"
                      disabled={removeMutation.isPending}
                      onClick={() => removeMutation.mutate(entry.instrumentId)}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
