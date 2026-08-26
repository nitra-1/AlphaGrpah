import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { DiscoveryCandidate } from '../types/discovery'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function DiscoveryPage() {
  const queryClient = useQueryClient()
  const [actioningSymbol, setActioningSymbol] = useState<string | null>(null)

  const listQuery = useQuery({
    queryKey: ['discovery'],
    queryFn: () => apiFetch<DiscoveryCandidate[]>('/admin/discovery'),
  })

  const discardMutation = useMutation({
    mutationFn: (symbol: string) => apiFetch<void>(`/admin/discovery/${symbol}/discard`, { method: 'POST' }),
    onMutate: (symbol) => setActioningSymbol(symbol),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['discovery'] }),
    onSettled: () => setActioningSymbol(null),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Discovery</h1>
      <p className="mt-1 text-sm text-text-muted">
        Real NSE bulk/block deal activity in stocks not currently tracked - institutional buying signal that would otherwise be silently
        discarded. Promote a symbol to start tracking it, or Discard to stop seeing it here.
      </p>

      {listQuery.isLoading && (
        <div className="mt-6 space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="rounded-2xl border border-border bg-surface p-5">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="mt-3 h-3 w-2/3" />
            </div>
          ))}
        </div>
      )}

      {listQuery.error && <ErrorState message="Couldn't load discovery candidates." onRetry={listQuery.refetch} />}

      {listQuery.data && listQuery.data.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">Nothing pending review - no untracked symbol has real bulk/block deal activity right now.</p>
      )}

      {listQuery.data && listQuery.data.length > 0 && (
        <div className="mt-6 space-y-3">
          {listQuery.data.map((candidate) => {
            const isActioning = actioningSymbol === candidate.symbol && discardMutation.isPending
            return (
              <div key={candidate.symbol} className="rounded-2xl border border-border bg-surface p-5">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <span className="font-semibold text-text">{candidate.symbol}</span>
                    {candidate.securityName && <span className="ml-2 text-text-muted">{candidate.securityName}</span>}
                    <p className="mt-0.5 text-xs text-text-muted">
                      {candidate.dealCount} deal{candidate.dealCount === 1 ? '' : 's'} &middot; {candidate.distinctBuyers} distinct{' '}
                      buyer{candidate.distinctBuyers === 1 ? '' : 's'} &middot; {candidate.totalQuantity.toLocaleString('en-IN')} shares &middot;{' '}
                      {formatDate(candidate.firstDealDate)} - {formatDate(candidate.latestDealDate)}
                    </p>
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-3">
                  <Link
                    to={`/admin/add-instrument?symbol=${encodeURIComponent(candidate.symbol)}`}
                    className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover"
                  >
                    Promote
                  </Link>
                  <button
                    className="rounded-lg border border-border px-4 py-2 text-sm font-semibold text-text-muted hover:bg-bg disabled:opacity-50"
                    disabled={isActioning}
                    onClick={() => discardMutation.mutate(candidate.symbol)}
                  >
                    {isActioning ? 'Discarding…' : 'Discard'}
                  </button>
                </div>
                {actioningSymbol === candidate.symbol && discardMutation.isError && (
                  <p className="mt-2 text-sm text-loss">Couldn't discard.</p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
