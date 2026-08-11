import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { RankingEntry } from '../types/rankings'
import { RatingBadge } from '../components/RatingBadge'
import { DomainScoreBar } from '../components/DomainScoreBar'
import { ErrorState } from '../components/ErrorState'
import { TableSkeleton } from '../components/TableSkeleton'

export function RankingsPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['rankings'],
    queryFn: () => apiFetch<RankingEntry[]>('/rankings'),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Rankings</h1>
      <p className="mt-1 text-sm text-text-muted">
        Every tracked instrument's current Swing Rank and Long-Term Rank, blending all six domain scores (Module 3.1).
      </p>

      {isLoading && <TableSkeleton columns={8} />}
      {error && <ErrorState message="Couldn't load rankings." onRetry={refetch} />}

      {data && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Swing Rank</th>
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Swing Score</th>
                <th className="px-4 py-3 font-medium">Rating</th>
                <th className="px-4 py-3 font-medium">Long-Term Rank</th>
                <th className="px-4 py-3 font-medium">Long-Term Score</th>
                <th className="px-4 py-3 font-medium">Rating</th>
                <th className="px-4 py-3 font-medium">Domains</th>
              </tr>
            </thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.instrumentId} className="border-b border-border last:border-0 hover:bg-bg">
                  <td className="px-4 py-3 font-semibold text-text">{entry.swingRank ?? '—'}</td>
                  <td className="px-4 py-3">
                    <Link to={`/instruments/${entry.instrumentId}`} className="font-semibold text-accent hover:underline">
                      {entry.symbol}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-text">{entry.swingScore?.toFixed(2) ?? '—'}</td>
                  <td className="px-4 py-3">
                    <RatingBadge rating={entry.swingRating} />
                  </td>
                  <td className="px-4 py-3 text-text">{entry.longTermRank ?? '—'}</td>
                  <td className="px-4 py-3 text-text">{entry.longTermScore?.toFixed(2) ?? '—'}</td>
                  <td className="px-4 py-3">
                    <RatingBadge rating={entry.longTermRating} />
                  </td>
                  <td className="px-4 py-3">
                    <div className="grid grid-cols-3 gap-x-3 gap-y-1">
                      <DomainScoreBar label="T" fullLabel="Technical" value={entry.technicalScore} />
                      <DomainScoreBar label="F" fullLabel="Fundamental" value={entry.fundamentalScore} />
                      <DomainScoreBar label="I" fullLabel="Institutional" value={entry.institutionalScore} />
                      <DomainScoreBar label="S" fullLabel="Sector" value={entry.sectorScore} />
                      <DomainScoreBar label="R" fullLabel="Risk" value={entry.riskScore} />
                      <DomainScoreBar label="C" fullLabel="Corporate" value={entry.corporateScore} />
                    </div>
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
