import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { DashboardSummary } from '../types/dashboard'
import { WidgetCard } from '../components/WidgetCard'
import { SignalBadge } from '../components/SignalBadge'
import { RatingBadge } from '../components/RatingBadge'
import { ErrorState } from '../components/ErrorState'
import { WidgetCardSkeleton } from '../components/WidgetCardSkeleton'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function DashboardPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => apiFetch<DashboardSummary>('/dashboard'),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Dashboard</h1>
      <p className="mt-1 text-sm text-text-muted">
        Corporate intelligence across all tracked instruments - orders, events, guidance, and news (Module 2.10).
      </p>

      {isLoading && (
        <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2">
          {Array.from({ length: 9 }).map((_, i) => (
            <WidgetCardSkeleton key={i} />
          ))}
        </div>
      )}
      {error && <ErrorState message="Couldn't load the dashboard." onRetry={refetch} />}

      {data && (
        <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2">
          <WidgetCard
            title="Corporate Score"
            subtitle="Every tracked instrument, highest first"
            isEmpty={data.corporateScores.length === 0}
            emptyLabel="No corporate scores yet."
          >
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-text-muted">
                  <th className="pb-2 font-medium">Symbol</th>
                  <th className="pb-2 font-medium">Score</th>
                  <th className="pb-2 font-medium">Rating</th>
                  <th className="pb-2 font-medium">Order Book</th>
                  <th className="pb-2 font-medium">Mgmt</th>
                  <th className="pb-2 font-medium">News</th>
                </tr>
              </thead>
              <tbody>
                {data.corporateScores.map((row) => (
                  <tr key={row.symbol} className="border-t border-border">
                    <td className="py-2 font-semibold text-text">{row.symbol}</td>
                    <td className="py-2 text-text">{row.corporateScore.toFixed(1)}</td>
                    <td className="py-2"><RatingBadge rating={row.corporateRating} /></td>
                    <td className="py-2 text-text-muted">{row.orderBookScore?.toFixed(1) ?? '—'}</td>
                    <td className="py-2 text-text-muted">{row.managementScore?.toFixed(1) ?? '—'}</td>
                    <td className="py-2 text-text-muted">{row.newsCatalystScore?.toFixed(1) ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </WidgetCard>

          <WidgetCard
            title="Top Catalysts"
            subtitle="Latest News Catalyst score, highest first"
            isEmpty={data.topCatalysts.length === 0}
            emptyLabel="No catalyst scores yet."
          >
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-text-muted">
                  <th className="pb-2 font-medium">Symbol</th>
                  <th className="pb-2 font-medium">Score</th>
                  <th className="pb-2 font-medium">Trend</th>
                  <th className="pb-2 font-medium">Recent</th>
                </tr>
              </thead>
              <tbody>
                {data.topCatalysts.map((row) => (
                  <tr key={row.symbol} className="border-t border-border">
                    <td className="py-2 font-semibold text-text">{row.symbol}</td>
                    <td className="py-2 text-text">{row.catalystScore.toFixed(1)}</td>
                    <td className="py-2"><SignalBadge signal={row.catalystTrend} /></td>
                    <td className="py-2 text-text-muted">{row.recentCatalystCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </WidgetCard>

          <WidgetCard
            title="Growth Visibility"
            subtitle="Management Commentary growth-visibility score, highest first"
            isEmpty={data.growthVisibility.length === 0}
            emptyLabel="No growth-visibility scores yet."
          >
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-text-muted">
                  <th className="pb-2 font-medium">Symbol</th>
                  <th className="pb-2 font-medium">Score</th>
                  <th className="pb-2 font-medium">Guidance Trend</th>
                  <th className="pb-2 font-medium">Credibility</th>
                </tr>
              </thead>
              <tbody>
                {data.growthVisibility.map((row) => (
                  <tr key={row.symbol} className="border-t border-border">
                    <td className="py-2 font-semibold text-text">{row.symbol}</td>
                    <td className="py-2 text-text">{row.growthVisibilityScore.toFixed(1)}</td>
                    <td className="py-2"><SignalBadge signal={row.guidanceTrend} /></td>
                    <td className="py-2 text-text-muted">{row.managementCredibility}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </WidgetCard>

          <WidgetCard
            title="Today's Biggest Orders"
            subtitle="New / tender-won orders, largest value first"
            isEmpty={data.biggestOrders.length === 0}
            emptyLabel="No new orders detected in the lookback window."
          >
            <ul className="space-y-3">
              {data.biggestOrders.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <span className="text-text-muted">{formatDate(row.detectedAt)}</span>
                  </div>
                  <p className="mt-1 text-text-muted">
                    {row.customerName ?? 'Unnamed customer'}
                    {row.orderValueCrore != null && ` — ₹${row.orderValueCrore.toFixed(1)} Cr`}
                    {row.lifecycleStage && ` (${row.lifecycleStage})`}
                  </p>
                </li>
              ))}
            </ul>
          </WidgetCard>

          <WidgetCard
            title="Corporate Events"
            subtitle="Detected across all tracked instruments"
            isEmpty={data.corporateEvents.length === 0}
            emptyLabel="No corporate events detected in the lookback window."
          >
            <ul className="space-y-3">
              {data.corporateEvents.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <SignalBadge signal={row.signal} />
                  </div>
                  <p className="mt-1 text-xs font-medium text-text-muted">
                    {row.eventType.replace('_', ' ')}{row.category && ` · ${row.category}`}
                  </p>
                  <p className="mt-1 text-text-muted">{row.summary}</p>
                </li>
              ))}
            </ul>
          </WidgetCard>

          <WidgetCard
            title="Management Guidance Changes"
            subtitle="Forward-looking guidance statements"
            isEmpty={data.guidanceChanges.length === 0}
            emptyLabel="No guidance changes detected in the lookback window."
          >
            <ul className="space-y-3">
              {data.guidanceChanges.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <SignalBadge signal={row.direction} />
                  </div>
                  <p className="mt-1 text-text-muted">
                    {row.metricType}{row.guidanceValue && `: ${row.guidanceValue}`}{row.guidancePeriod && ` (${row.guidancePeriod})`}
                  </p>
                </li>
              ))}
            </ul>
          </WidgetCard>

          <WidgetCard
            title="Positive News"
            isEmpty={data.positiveNews.length === 0}
            emptyLabel="No positive news in the lookback window."
          >
            <ul className="space-y-3">
              {data.positiveNews.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <span className="text-text-muted">{formatDate(row.announcedAt)}</span>
                  </div>
                  <p className="mt-1 text-text-muted">{row.impactSummary}</p>
                </li>
              ))}
            </ul>
          </WidgetCard>

          <WidgetCard
            title="Negative News"
            isEmpty={data.negativeNews.length === 0}
            emptyLabel="No negative news in the lookback window."
          >
            <ul className="space-y-3">
              {data.negativeNews.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <span className="text-text-muted">{formatDate(row.announcedAt)}</span>
                  </div>
                  <p className="mt-1 text-text-muted">{row.impactSummary}</p>
                </li>
              ))}
            </ul>
          </WidgetCard>

          <WidgetCard
            title="Price Adjustments"
            subtitle="Bonus/split actions - historical prices for these instruments are now back-adjusted"
            isEmpty={data.priceAdjustments.length === 0}
            emptyLabel="No bonus or split actions in the lookback window - no price history has been adjusted."
          >
            <ul className="space-y-3">
              {data.priceAdjustments.map((row, i) => (
                <li key={i} className="border-t border-border pt-3 first:border-0 first:pt-0">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-text">{row.symbol}</span>
                    <SignalBadge signal={row.actionType} />
                  </div>
                  <p className="mt-1 text-text-muted">
                    {row.ratioNumerator != null && row.ratioDenominator != null && `${row.ratioNumerator}:${row.ratioDenominator} · `}
                    ex-date {formatDate(row.exDate)} · prices before this date scaled by {row.adjustmentFactor.toFixed(4)}x
                  </p>
                </li>
              ))}
            </ul>
          </WidgetCard>
        </div>
      )}
    </div>
  )
}
