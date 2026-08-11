import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { ComparisonEntry } from '../types/comparison'
import type { RankingEntry } from '../types/rankings'
import { RatingBadge } from '../components/RatingBadge'
import { DomainScoreBar } from '../components/DomainScoreBar'
import { ErrorState } from '../components/ErrorState'
import { TableSkeleton } from '../components/TableSkeleton'

export function ComparePage() {
  const [selectedIds, setSelectedIds] = useState<string[]>([])

  const rankingsQuery = useQuery({
    queryKey: ['rankings'],
    queryFn: () => apiFetch<RankingEntry[]>('/rankings'),
  })

  const comparisonQuery = useQuery({
    queryKey: ['comparison', [...selectedIds].sort()],
    queryFn: () => {
      const params = new URLSearchParams()
      selectedIds.forEach((id) => params.append('instrumentIds', id))
      return apiFetch<ComparisonEntry[]>(`/comparison?${params.toString()}`)
    },
    enabled: selectedIds.length > 0,
  })

  function toggle(instrumentId: string) {
    setSelectedIds((prev) =>
      prev.includes(instrumentId) ? prev.filter((id) => id !== instrumentId) : [...prev, instrumentId]
    )
  }

  const entriesById = new Map((comparisonQuery.data ?? []).map((entry) => [entry.instrumentId, entry]))
  const orderedEntries = selectedIds.map((id) => entriesById.get(id)).filter((e): e is ComparisonEntry => !!e)

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Compare</h1>
      <p className="mt-1 text-sm text-text-muted">
        Any instruments side by side across all six domain scores plus Swing/Long-Term Score and Rank (Module 3.5).
      </p>

      <div className="mt-6 flex flex-wrap gap-2">
        {(rankingsQuery.data ?? []).map((entry) => {
          const checked = selectedIds.includes(entry.instrumentId)
          return (
            <label
              key={entry.instrumentId}
              className={`flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-medium ${
                checked ? 'border-accent bg-accent-soft text-accent' : 'border-border bg-surface text-text-muted'
              }`}
            >
              <input
                type="checkbox"
                className="sr-only"
                checked={checked}
                onChange={() => toggle(entry.instrumentId)}
              />
              {entry.symbol}
            </label>
          )
        })}
      </div>

      {selectedIds.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">Select instruments above to compare them side by side.</p>
      )}

      {selectedIds.length > 0 && comparisonQuery.isLoading && (
        <TableSkeleton columns={selectedIds.length + 1} rows={14} />
      )}
      {comparisonQuery.error && <ErrorState message="Couldn't load the comparison." onRetry={comparisonQuery.refetch} />}

      {orderedEntries.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <tbody>
              <tr className="border-b border-border">
                <th className="w-40 shrink-0 px-4 py-3 text-xs font-medium text-text-muted">Symbol</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 font-semibold text-text">{entry.symbol}</td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">As Of</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text-muted">{entry.asOfDate ?? '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border bg-bg">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Swing Score</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text">{entry.swingScore?.toFixed(2) ?? '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border bg-bg">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Swing Rating</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><RatingBadge rating={entry.swingRating} /></td>
                ))}
              </tr>
              <tr className="border-b border-border bg-bg">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Swing Rank</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text">{entry.swingRank ?? '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Long-Term Score</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text">{entry.longTermScore?.toFixed(2) ?? '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Long-Term Rating</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><RatingBadge rating={entry.longTermRating} /></td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Long-Term Rank</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text">{entry.longTermRank ?? '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border bg-bg">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Confidence</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3 text-text">{entry.confidence != null ? `${entry.confidence.toFixed(0)}%` : '—'}</td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Technical</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="T" fullLabel="Technical" value={entry.technicalScore} /></td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Fundamental</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="F" fullLabel="Fundamental" value={entry.fundamentalScore} /></td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Institutional</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="I" fullLabel="Institutional" value={entry.institutionalScore} /></td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Sector</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="S" fullLabel="Sector" value={entry.sectorScore} /></td>
                ))}
              </tr>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Risk</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="R" fullLabel="Risk" value={entry.riskScore} /></td>
                ))}
              </tr>
              <tr className="last:border-0">
                <th className="px-4 py-3 text-xs font-medium text-text-muted">Corporate</th>
                {orderedEntries.map((entry) => (
                  <td key={entry.instrumentId} className="px-4 py-3"><DomainScoreBar label="C" fullLabel="Corporate" value={entry.corporateScore} /></td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
