import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { DiscoveryCandidate, DiscoveryDealDetail } from '../types/discovery'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'
import { MaterialityBadge } from '../components/MaterialityBadge'
import { FlowStateBadge } from '../components/FlowStateBadge'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

function formatRupees(value: number) {
  return `₹${value.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`
}

function formatRatio(ratio: number) {
  return `${ratio.toFixed(2)}x`
}

export function DiscoveryPage() {
  const queryClient = useQueryClient()
  const [actioningSymbol, setActioningSymbol] = useState<string | null>(null)
  const [expandedSymbol, setExpandedSymbol] = useState<string | null>(null)

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
        discarded. Materiality (Deal Value / 20-day ADTV, repetition, breadth) ranks how significant a deal is; reported net flow is a
        separate, deliberately distinct signal - disclosed deals leaning buy-side isn't proof of genuine accumulation. Promote a symbol to
        start tracking it, or Discard to stop seeing it here.
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
            const isExpanded = expandedSymbol === candidate.symbol
            return (
              <div key={candidate.symbol} className="rounded-2xl border border-border bg-surface p-5">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-text">{candidate.symbol}</span>
                      {candidate.securityName && <span className="text-text-muted">{candidate.securityName}</span>}
                      <MaterialityBadge level={candidate.maxMaterialityLevel} />
                      {candidate.maxMaterialityScore != null && (
                        <span className="text-xs text-text-muted">{candidate.maxMaterialityScore.toFixed(0)}/100</span>
                      )}
                    </div>
                    <p className="mt-0.5 text-xs text-text-muted">
                      {candidate.dealCount} deal{candidate.dealCount === 1 ? '' : 's'} &middot; {candidate.distinctBuyers} distinct{' '}
                      buyer{candidate.distinctBuyers === 1 ? '' : 's'} &middot; {candidate.totalQuantity.toLocaleString('en-IN')} shares &middot;{' '}
                      {formatDate(candidate.firstDealDate)} - {formatDate(candidate.latestDealDate)}
                      {candidate.largestDealToAdtvRatio != null && (
                        <> &middot; largest deal {formatRatio(candidate.largestDealToAdtvRatio)} ADTV</>
                      )}
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
                  <button
                    className="ml-auto text-sm font-semibold text-accent hover:text-accent-hover"
                    onClick={() => setExpandedSymbol(isExpanded ? null : candidate.symbol)}
                  >
                    {isExpanded ? 'Hide deals' : 'Show deals'}
                  </button>
                </div>
                {actioningSymbol === candidate.symbol && discardMutation.isError && (
                  <p className="mt-2 text-sm text-loss">Couldn't discard.</p>
                )}
                {isExpanded && <DealList symbol={candidate.symbol} />}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function DealList({ symbol }: { symbol: string }) {
  const dealsQuery = useQuery({
    queryKey: ['discovery', symbol, 'deals'],
    queryFn: () => apiFetch<DiscoveryDealDetail[]>(`/admin/discovery/${symbol}/deals`),
  })

  return (
    <div className="mt-4 overflow-x-auto rounded-xl border border-border">
      {dealsQuery.isLoading && <div className="p-4"><Skeleton className="h-4 w-full" /></div>}
      {dealsQuery.error && <ErrorState message="Couldn't load deals." onRetry={dealsQuery.refetch} />}
      {dealsQuery.data && (
        <table className="w-full text-left text-sm">
          <thead className="bg-bg text-xs uppercase text-text-muted">
            <tr>
              <th className="px-3 py-2">Date</th>
              <th className="px-3 py-2">Client</th>
              <th className="px-3 py-2">Side</th>
              <th className="px-3 py-2">Quantity</th>
              <th className="px-3 py-2">Deal value</th>
              <th className="px-3 py-2">Type</th>
              <th className="px-3 py-2">Materiality</th>
              <th className="px-3 py-2">Deal/ADTV</th>
              <th className="px-3 py-2">Reported flow</th>
            </tr>
          </thead>
          <tbody>
            {dealsQuery.data.map((deal) => (
              <tr key={deal.id} className="border-t border-border">
                <td className="px-3 py-2 text-text-muted">{formatDate(deal.dealDate)}</td>
                <td className="px-3 py-2 text-text">{deal.clientName}</td>
                <td className={`px-3 py-2 font-semibold ${deal.buySell === 'BUY' ? 'text-gain' : 'text-loss'}`}>{deal.buySell}</td>
                <td className="px-3 py-2 text-text-muted">{deal.quantity.toLocaleString('en-IN')}</td>
                <td className="px-3 py-2 text-text-muted">{formatRupees(deal.dealValue)}</td>
                <td className="px-3 py-2 text-text-muted">{deal.dealType}</td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-1.5">
                    <MaterialityBadge level={deal.materialityLevel} />
                    {deal.materialityScore != null && <span className="text-xs text-text-muted">{deal.materialityScore.toFixed(0)}</span>}
                  </div>
                </td>
                <td className="px-3 py-2 text-text-muted">{deal.dealToAdtvRatio != null ? formatRatio(deal.dealToAdtvRatio) : '—'}</td>
                <td className="px-3 py-2"><FlowStateBadge state={deal.reportedFlowState} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
