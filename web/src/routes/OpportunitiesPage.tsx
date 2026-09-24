import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { OpportunityEntry } from '../types/opportunities'
import { LifecycleBadge } from '../components/LifecycleBadge'
import { TrajectoryBadge } from '../components/TrajectoryBadge'
import { FreshnessNote } from '../components/FreshnessNote'
import { ErrorState } from '../components/ErrorState'
import { TableSkeleton } from '../components/TableSkeleton'

export function OpportunitiesPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['opportunities'],
    queryFn: () => apiFetch<OpportunityEntry[]>('/opportunities'),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Opportunities</h1>
      <p className="mt-1 text-sm text-text-muted">
        Every tracked instrument's current transformation lifecycle - where it sits in the Stage 1-5 discovery pipeline (evidence → inflection → sequence → cross-domain convergence → lifecycle).
      </p>

      {isLoading && <TableSkeleton columns={8} />}
      {error && <ErrorState message="Couldn't load opportunities." onRetry={refetch} />}

      {data && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Lifecycle</th>
                <th className="px-4 py-3 font-medium">Trajectory</th>
                <th className="px-4 py-3 font-medium">Strength</th>
                <th className="px-4 py-3 font-medium">Convergence Score</th>
                <th className="px-4 py-3 font-medium">Active Domains</th>
                <th className="px-4 py-3 font-medium">Age (days)</th>
                <th className="px-4 py-3 font-medium">As Of</th>
              </tr>
            </thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.instrumentId} className="border-b border-border last:border-0 hover:bg-bg">
                  <td className="px-4 py-3">
                    <Link to={`/opportunities/${entry.instrumentId}`} className="font-semibold text-accent hover:underline">
                      {entry.symbol}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <LifecycleBadge state={entry.lifecycleState} readiness={entry.lifecycleReadiness} />
                  </td>
                  <td className="px-4 py-3">
                    <TrajectoryBadge direction={entry.trajectoryDirection} />
                  </td>
                  <td className="px-4 py-3 text-text">{entry.lifecycleStrength?.toFixed(1) ?? '—'}</td>
                  <td className="px-4 py-3 text-text">{entry.currentConvergenceScore?.toFixed(1) ?? '—'}</td>
                  <td className="px-4 py-3 text-text">{entry.currentActiveDomains ?? '—'}</td>
                  <td className="px-4 py-3 text-text">{entry.lifecycleAgeDays ?? '—'}</td>
                  <td className="px-4 py-3">
                    <FreshnessNote asOfDate={entry.asOfDate} />
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
