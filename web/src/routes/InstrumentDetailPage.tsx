import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiFetch, ApiError } from '../lib/api'
import type { RankingEntry } from '../types/rankings'
import type { AnalystExplanation } from '../types/analyst'
import { RatingBadge } from '../components/RatingBadge'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'

function DomainScore({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="rounded-xl border border-border bg-bg px-4 py-3">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold text-text">{value?.toFixed(1) ?? '—'}</p>
    </div>
  )
}

function AnalystCard({
  title,
  description,
  buttonLabel,
  path,
}: {
  title: string
  description: string
  buttonLabel: string
  path: string
}) {
  const query = useQuery({
    queryKey: ['analyst', path],
    queryFn: () => apiFetch<AnalystExplanation>(path),
    enabled: false,
    retry: false,
  })

  return (
    <div className="rounded-2xl border border-border bg-surface p-5">
      <h2 className="text-sm font-semibold text-text">{title}</h2>
      <p className="mt-0.5 text-xs text-text-muted">{description}</p>

      {!query.data && (
        <button
          className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
          disabled={query.isFetching}
          onClick={() => query.refetch()}
        >
          {query.isFetching ? 'Asking AI Analyst…' : buttonLabel}
        </button>
      )}

      {query.isFetching && (
        <p className="mt-3 text-xs text-text-muted">This calls Claude live and can take a few seconds.</p>
      )}

      {query.error && (
        <p className="mt-3 text-sm text-loss">
          {query.error instanceof ApiError ? query.error.message : "Couldn't get an explanation."}
        </p>
      )}

      {query.data && (
        <div className="mt-4">
          <p className="whitespace-pre-line text-sm leading-relaxed text-text">{query.data.explanation}</p>
          <button
            className="mt-3 text-xs font-semibold text-accent hover:underline"
            onClick={() => query.refetch()}
            disabled={query.isFetching}
          >
            Ask again
          </button>
        </div>
      )}
    </div>
  )
}

export function InstrumentDetailPage() {
  const { instrumentId } = useParams<{ instrumentId: string }>()

  const rankingsQuery = useQuery({
    queryKey: ['rankings'],
    queryFn: () => apiFetch<RankingEntry[]>('/rankings'),
  })

  const entry = rankingsQuery.data?.find((e) => e.instrumentId === instrumentId)

  if (rankingsQuery.isLoading) {
    return (
      <div>
        <Skeleton className="h-4 w-32" />
        <div className="mt-3 flex items-baseline justify-between">
          <Skeleton className="h-7 w-24" />
          <Skeleton className="h-3 w-28" />
        </div>
        <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="rounded-2xl border border-border bg-surface p-5">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="mt-3 h-8 w-40" />
            </div>
          ))}
        </div>
        <div className="mt-5 rounded-2xl border border-border bg-surface p-5">
          <Skeleton className="h-4 w-28" />
          <Skeleton className="mt-2 h-3 w-64" />
          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="rounded-xl border border-border bg-bg px-4 py-3">
                <Skeleton className="h-3 w-16" />
                <Skeleton className="mt-2 h-5 w-10" />
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (rankingsQuery.error) {
    return (
      <div>
        <Link to="/" className="text-sm font-semibold text-accent hover:underline">← Back to Rankings</Link>
        <ErrorState message="Couldn't load this instrument." onRetry={rankingsQuery.refetch} />
      </div>
    )
  }

  if (!entry) {
    return (
      <div>
        <Link to="/" className="text-sm font-semibold text-accent hover:underline">← Back to Rankings</Link>
        <p className="mt-4 text-sm text-text-muted">No instrument found with that id.</p>
      </div>
    )
  }

  return (
    <div>
      <Link to="/" className="text-sm font-semibold text-accent hover:underline">← Back to Rankings</Link>

      <div className="mt-3 flex items-baseline justify-between">
        <h1 className="text-2xl font-bold text-text">{entry.symbol}</h1>
        <span className="text-xs text-text-muted">As of {entry.asOfDate ?? '—'}</span>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2">
        <div className="rounded-2xl border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-text">Swing (Short-Term)</h2>
          <div className="mt-3 flex items-center gap-3">
            <span className="text-2xl font-bold text-text">{entry.swingScore?.toFixed(2) ?? '—'}</span>
            <RatingBadge rating={entry.swingRating} />
            {entry.swingRank && <span className="text-sm text-text-muted">Rank #{entry.swingRank}</span>}
          </div>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-text">Long-Term</h2>
          <div className="mt-3 flex items-center gap-3">
            <span className="text-2xl font-bold text-text">{entry.longTermScore?.toFixed(2) ?? '—'}</span>
            <RatingBadge rating={entry.longTermRating} />
            {entry.longTermRank && <span className="text-sm text-text-muted">Rank #{entry.longTermRank}</span>}
          </div>
        </div>
      </div>

      <div className="mt-5 rounded-2xl border border-border bg-surface p-5">
        <h2 className="text-sm font-semibold text-text">Domain Scores</h2>
        <p className="mt-0.5 text-xs text-text-muted">The six inputs blended into Swing/Long-Term Score (Module 3.1)</p>
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <DomainScore label="Technical" value={entry.technicalScore} />
          <DomainScore label="Fundamental" value={entry.fundamentalScore} />
          <DomainScore label="Institutional" value={entry.institutionalScore} />
          <DomainScore label="Sector" value={entry.sectorScore} />
          <DomainScore label="Risk" value={entry.riskScore} />
          <DomainScore label="Corporate" value={entry.corporateScore} />
        </div>
        {entry.confidence != null && (
          <p className="mt-3 text-xs text-text-muted">Confidence: {entry.confidence.toFixed(0)}%</p>
        )}
      </div>

      <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-2">
        <AnalystCard
          title="Why this Corporate Score?"
          description="AI-narrated explanation grounded in deterministic facts (Module 3.7)"
          buttonLabel="Explain Corporate Score"
          path={`/analyst/${instrumentId}/score-explanation`}
        />
        <AnalystCard
          title="Why this Swing Rank?"
          description="AI-narrated explanation of the rank change, grounded in deterministic facts (Module 3.7)"
          buttonLabel="Explain Swing Rank"
          path={`/analyst/${instrumentId}/rank-explanation`}
        />
      </div>
    </div>
  )
}
