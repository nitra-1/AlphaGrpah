import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { LearningEvidenceSummary, RatingHitRate } from '../types/learning'
import { StatTile } from '../components/StatTile'
import { WidgetCard } from '../components/WidgetCard'
import { ErrorState } from '../components/ErrorState'
import { WidgetCardSkeleton } from '../components/WidgetCardSkeleton'

// Must match learning.performance.ModelPerformanceReader.MIN_SAMPLE_SIZE on the backend.
const MIN_SAMPLE_SIZE = 10

const HORIZONS = [5, 10, 20, 60]
const SCORED_RATINGS = ['STRONG_BUY', 'BUY', 'REDUCE', 'AVOID']

function HitRateTable({ title, subtitle, rows }: { title: string; subtitle: string; rows: RatingHitRate[] }) {
  const cell = (rating: string, horizon: number) => rows.find((r) => r.rating === rating && r.horizonDays === horizon)

  return (
    <WidgetCard title={title} subtitle={subtitle} isEmpty={rows.every((r) => r.sampleSize === 0)} emptyLabel="No outcomes measured yet for this rating.">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="text-xs text-text-muted">
            <th className="pb-2 font-medium">Rating</th>
            {HORIZONS.map((h) => (
              <th key={h} className="pb-2 font-medium">{h}D</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {SCORED_RATINGS.map((rating) => (
            <tr key={rating} className="border-t border-border">
              <td className="py-2 font-semibold text-text">{rating.replace('_', ' ')}</td>
              {HORIZONS.map((horizon) => {
                const entry = cell(rating, horizon)
                return (
                  <td key={horizon} className="py-2 text-text-muted">
                    {!entry || entry.sampleSize === 0
                      ? '—'
                      : entry.sufficientData
                        ? `${entry.hitRatePercentage!.toFixed(0)}% (n=${entry.sampleSize})`
                        : `Not enough data yet (n=${entry.sampleSize} of ${MIN_SAMPLE_SIZE})`}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </WidgetCard>
  )
}

export function LearningPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['learning-evidence-summary'],
    queryFn: () => apiFetch<LearningEvidenceSummary>('/learning/evidence-summary'),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Learning</h1>
      <p className="mt-1 text-sm text-text-muted">
        Phase 4A - Observation Mode. AlphaGraph is accumulating the evidence a future learning system will need: what it rated each instrument,
        and what actually happened afterward. No pattern mining, weight optimization, or probability modeling runs yet - those activate later,
        gated on having enough real data, not a calendar date.
      </p>

      {isLoading && (
        <div className="mt-6 space-y-5">
          <WidgetCardSkeleton />
          <WidgetCardSkeleton />
        </div>
      )}

      {error && <ErrorState message="Couldn't load the learning evidence summary." onRetry={refetch} />}

      {data && (
        <div className="mt-6 space-y-5">
          <WidgetCard title="Evidence Accumulated" subtitle="Raw counters - the honest 'how much do we actually have' picture" isEmpty={false}>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <StatTile label="Days of History" value={data.daysOfHistory} />
              <StatTile label="Decision Snapshots Archived" value={data.decisionSnapshotCount} />
              <StatTile label="Forward Outcomes Measured" value={data.forwardOutcomesComputed} />
            </div>
          </WidgetCard>

          <HitRateTable
            title="Swing Rating Performance"
            subtitle="Was the short-term rating directionally correct N trading days later? Hidden until at least 10 outcomes exist per cell."
            rows={data.swingRatingHitRates}
          />

          <HitRateTable
            title="Long-Term Rating Performance"
            subtitle="Same measurement for the long-term rating."
            rows={data.longTermRatingHitRates}
          />
        </div>
      )}
    </div>
  )
}
