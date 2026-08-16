import { Fragment, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { PortfolioEntry, PortfolioRisk } from '../types/portfolio'
import type { RankingEntry } from '../types/rankings'
import { RatingBadge } from '../components/RatingBadge'
import { LevelBadge } from '../components/LevelBadge'
import { PositionHealthBadge } from '../components/PositionHealthBadge'
import { WidgetCard } from '../components/WidgetCard'
import { ErrorState } from '../components/ErrorState'
import { TableSkeleton } from '../components/TableSkeleton'
import { WidgetCardSkeleton } from '../components/WidgetCardSkeleton'

function formatMoney(value: number | null) {
  if (value == null) return '—'
  return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatPercent(value: number | null) {
  if (value == null) return '—'
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

function healthTooltip(entry: PortfolioEntry): string {
  if (!entry.positionHealth) return 'No Decision Score exists as of this holding\'s entry date yet.'
  const lines: string[] = []
  const anchorLabel = entry.healthAnchorType === 'FIRST_ENTRY' ? 'First entry' : entry.healthAnchorType ?? 'Entry'
  lines.push(`Health measured since: ${anchorLabel} - ${entry.healthAnchorDate ?? '—'}`)
  if (entry.entrySwingScore != null && entry.swingScoreChange != null) {
    const current = entry.entrySwingScore + entry.swingScoreChange
    lines.push(`Swing Score: ${entry.entrySwingScore.toFixed(1)} -> ${current.toFixed(1)} (${formatPercent(entry.swingScoreChange).replace('%', '')})`)
  }
  if (entry.entrySwingRank != null && entry.swingRankChange != null) {
    const current = entry.entrySwingRank - entry.swingRankChange
    lines.push(`Swing Rank: #${entry.entrySwingRank} -> #${current}`)
  }
  if (entry.healthReason) {
    lines.push(`Reason: ${entry.healthReason.replace(/_/g, ' ')}`)
  }
  if (entry.attentionLevel) {
    lines.push(`Attention: ${entry.attentionLevel}`)
  }
  for (const delta of entry.domainDeltas) {
    lines.push(`${delta.domain}: ${delta.entryValue.toFixed(1)} -> ${delta.currentValue.toFixed(1)} (${delta.delta >= 0 ? '+' : ''}${delta.delta.toFixed(1)})`)
  }
  return lines.join('\n')
}

export function PortfolioPage() {
  const queryClient = useQueryClient()

  const [buyInstrumentId, setBuyInstrumentId] = useState('')
  const [buyQuantity, setBuyQuantity] = useState('')
  const [buyPrice, setBuyPrice] = useState('')
  const [buyRationale, setBuyRationale] = useState('')

  const [sellingId, setSellingId] = useState<string | null>(null)
  const [sellQuantity, setSellQuantity] = useState('')
  const [sellPrice, setSellPrice] = useState('')
  const [sellRationale, setSellRationale] = useState('')

  const [deletingId, setDeletingId] = useState<string | null>(null)

  const portfolioQuery = useQuery({
    queryKey: ['portfolio'],
    queryFn: () => apiFetch<PortfolioEntry[]>('/portfolio'),
  })

  const riskQuery = useQuery({
    queryKey: ['portfolio-risk'],
    queryFn: () => apiFetch<PortfolioRisk>('/portfolio/risk'),
  })

  const rankingsQuery = useQuery({
    queryKey: ['rankings'],
    queryFn: () => apiFetch<RankingEntry[]>('/rankings'),
  })

  function refreshPortfolio() {
    queryClient.invalidateQueries({ queryKey: ['portfolio'] })
    queryClient.invalidateQueries({ queryKey: ['portfolio-risk'] })
  }

  const buyMutation = useMutation({
    mutationFn: () =>
      apiFetch<PortfolioEntry>('/portfolio/buy', {
        method: 'POST',
        body: JSON.stringify({
          instrumentId: buyInstrumentId,
          quantity: buyQuantity,
          price: buyPrice,
          rationale: buyRationale || null,
        }),
      }),
    onSuccess: () => {
      refreshPortfolio()
      setBuyInstrumentId('')
      setBuyQuantity('')
      setBuyPrice('')
      setBuyRationale('')
    },
  })

  const sellMutation = useMutation({
    mutationFn: (instrumentId: string) =>
      apiFetch<PortfolioEntry>('/portfolio/sell', {
        method: 'POST',
        body: JSON.stringify({
          instrumentId,
          quantity: sellQuantity,
          price: sellPrice,
          rationale: sellRationale || null,
        }),
      }),
    onSuccess: () => {
      refreshPortfolio()
      setSellingId(null)
      setSellQuantity('')
      setSellPrice('')
      setSellRationale('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (instrumentId: string) => apiFetch<void>(`/portfolio/${instrumentId}`, { method: 'DELETE' }),
    onSuccess: () => {
      refreshPortfolio()
      setDeletingId(null)
    },
  })

  const risk = riskQuery.data

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Portfolio</h1>
      <p className="mt-1 text-sm text-text-muted">
        Your portfolio - current holdings, live P&L, and Score/Rank/Risk (Module 3.3), plus aggregate portfolio risk (Module 3.4).
      </p>

      <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
        <h2 className="text-sm font-semibold text-text">Buy</h2>
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <select
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
            value={buyInstrumentId}
            onChange={(e) => setBuyInstrumentId(e.target.value)}
          >
            <option value="">Instrument…</option>
            {(rankingsQuery.data ?? []).map((entry) => (
              <option key={entry.instrumentId} value={entry.instrumentId}>
                {entry.symbol}
              </option>
            ))}
          </select>
          <input
            className="w-28 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
            type="number"
            min="0"
            step="any"
            placeholder="Quantity"
            value={buyQuantity}
            onChange={(e) => setBuyQuantity(e.target.value)}
          />
          <input
            className="w-32 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
            type="number"
            min="0"
            step="any"
            placeholder="Price"
            value={buyPrice}
            onChange={(e) => setBuyPrice(e.target.value)}
          />
          <input
            className="min-w-[12rem] flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
            type="text"
            placeholder="Rationale (optional)"
            value={buyRationale}
            onChange={(e) => setBuyRationale(e.target.value)}
          />
          <button
            className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
            disabled={!buyInstrumentId || !buyQuantity || !buyPrice || buyMutation.isPending}
            onClick={() => buyMutation.mutate()}
          >
            Buy
          </button>
        </div>
        {buyMutation.isError && <p className="mt-2 text-sm text-loss">Couldn't complete that buy.</p>}
      </div>

      {portfolioQuery.isLoading && <TableSkeleton columns={11} />}
      {portfolioQuery.error && <ErrorState message="Couldn't load the portfolio." onRetry={portfolioQuery.refetch} />}

      {portfolioQuery.data && portfolioQuery.data.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">No holdings yet - buy an instrument above.</p>
      )}

      {portfolioQuery.data && portfolioQuery.data.length > 0 && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Qty</th>
                <th className="px-4 py-3 font-medium">Avg Buy</th>
                <th className="px-4 py-3 font-medium">Current</th>
                <th className="px-4 py-3 font-medium">Market Value</th>
                <th className="px-4 py-3 font-medium">Unrealized P&L</th>
                <th className="px-4 py-3 font-medium">Swing</th>
                <th className="px-4 py-3 font-medium">Health</th>
                <th className="px-4 py-3 font-medium">Long-Term</th>
                <th className="px-4 py-3 font-medium">Risk</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {portfolioQuery.data.map((entry) => (
                <Fragment key={entry.instrumentId}>
                  <tr className="border-b border-border last:border-0 hover:bg-bg">
                    <td className="px-4 py-3 font-semibold text-text">{entry.symbol}</td>
                    <td className="px-4 py-3 text-text">{entry.quantity}</td>
                    <td className="px-4 py-3 text-text">{formatMoney(entry.avgBuyPrice)}</td>
                    <td className="px-4 py-3 text-text">{formatMoney(entry.currentPrice)}</td>
                    <td className="px-4 py-3 text-text">{formatMoney(entry.marketValue)}</td>
                    <td className={`px-4 py-3 font-medium ${entry.unrealizedPnl == null ? 'text-text-muted' : entry.unrealizedPnl >= 0 ? 'text-gain' : 'text-loss'}`}>
                      {formatMoney(entry.unrealizedPnl)} <span className="text-xs">({formatPercent(entry.unrealizedPnlPercent)})</span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        <RatingBadge rating={entry.swingRating} />
                        {entry.swingRank && <span className="text-xs text-text-muted">#{entry.swingRank}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3" title={healthTooltip(entry)}>
                      <PositionHealthBadge health={entry.positionHealth} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5">
                        <RatingBadge rating={entry.longTermRating} />
                        {entry.longTermRank && <span className="text-xs text-text-muted">#{entry.longTermRank}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3"><LevelBadge level={entry.riskLevel} /></td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-3">
                        <button
                          className="text-xs font-semibold text-accent hover:underline"
                          onClick={() => {
                            setDeletingId(null)
                            setSellingId(sellingId === entry.instrumentId ? null : entry.instrumentId)
                          }}
                        >
                          Sell
                        </button>
                        <button
                          className="text-xs font-semibold text-loss hover:underline"
                          onClick={() => {
                            setSellingId(null)
                            setDeletingId(deletingId === entry.instrumentId ? null : entry.instrumentId)
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                  {sellingId === entry.instrumentId && (
                    <tr className="border-b border-border bg-bg">
                      <td colSpan={11} className="px-4 py-3">
                        <div className="flex flex-wrap items-end gap-3">
                          <input
                            className="w-28 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
                            type="number"
                            min="0"
                            step="any"
                            placeholder="Quantity"
                            value={sellQuantity}
                            onChange={(e) => setSellQuantity(e.target.value)}
                          />
                          <input
                            className="w-32 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
                            type="number"
                            min="0"
                            step="any"
                            placeholder="Price"
                            value={sellPrice}
                            onChange={(e) => setSellPrice(e.target.value)}
                          />
                          <input
                            className="min-w-[12rem] flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
                            type="text"
                            placeholder="Rationale (optional)"
                            value={sellRationale}
                            onChange={(e) => setSellRationale(e.target.value)}
                          />
                          <button
                            className="rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
                            disabled={!sellQuantity || !sellPrice || sellMutation.isPending}
                            onClick={() => sellMutation.mutate(entry.instrumentId)}
                          >
                            Confirm Sell
                          </button>
                        </div>
                        {sellMutation.isError && <p className="mt-2 text-sm text-loss">Couldn't complete that sell.</p>}
                      </td>
                    </tr>
                  )}
                  {deletingId === entry.instrumentId && (
                    <tr className="border-b border-border bg-bg">
                      <td colSpan={11} className="px-4 py-3">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <p className="text-sm text-text">
                            Delete {entry.symbol} from your portfolio? This removes the holding without recording a trade
                            - it will not appear in Trade Journal. Use Sell instead if this is a real exit.
                          </p>
                          <div className="flex items-center gap-3">
                            <button
                              className="text-xs font-semibold text-text-muted hover:underline"
                              onClick={() => setDeletingId(null)}
                            >
                              Cancel
                            </button>
                            <button
                              className="rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
                              disabled={deleteMutation.isPending}
                              onClick={() => deleteMutation.mutate(entry.instrumentId)}
                            >
                              Confirm Delete
                            </button>
                          </div>
                        </div>
                        {deleteMutation.isError && <p className="mt-2 text-sm text-loss">Couldn't delete that holding.</p>}
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {riskQuery.isLoading && (
        <div className="mt-6">
          <WidgetCardSkeleton />
        </div>
      )}

      {riskQuery.error && (
        <ErrorState message="Couldn't load portfolio risk." onRetry={riskQuery.refetch} />
      )}

      {risk && (
        <div className="mt-6">
          <WidgetCard
            title="Portfolio Risk"
            subtitle="Market-value-weighted risk and concentration across the whole portfolio"
            isEmpty={risk.totalMarketValue == null}
            emptyLabel="No priced holdings to assess risk against yet."
          >
            <div className="grid grid-cols-2 gap-6 sm:grid-cols-4">
              <div>
                <p className="text-xs text-text-muted">Weighted Risk</p>
                <div className="mt-1 flex items-center gap-2">
                  <span className="text-text">{risk.weightedRiskScore?.toFixed(1) ?? '—'}</span>
                  <LevelBadge level={risk.weightedRiskLevel} />
                </div>
                {risk.riskScoreCoveragePercent != null && (
                  <p className="mt-0.5 text-xs text-text-muted">{risk.riskScoreCoveragePercent.toFixed(0)}% coverage</p>
                )}
              </div>
              <div>
                <p className="text-xs text-text-muted">Top Holding</p>
                <div className="mt-1 flex items-center gap-2">
                  <span className="text-text">{risk.topHoldingSymbol ?? '—'} ({risk.topHoldingConcentrationPercent?.toFixed(1) ?? '—'}%)</span>
                </div>
                <div className="mt-0.5"><LevelBadge level={risk.holdingConcentrationLevel} /></div>
              </div>
              <div>
                <p className="text-xs text-text-muted">Top Sector</p>
                <div className="mt-1 flex items-center gap-2">
                  <span className="text-text">{risk.topSectorName ?? '—'} ({risk.topSectorConcentrationPercent?.toFixed(1) ?? '—'}%)</span>
                </div>
                <div className="mt-0.5"><LevelBadge level={risk.sectorConcentrationLevel} /></div>
              </div>
              <div>
                <p className="text-xs text-text-muted">Total Market Value</p>
                <p className="mt-1 text-text">{formatMoney(risk.totalMarketValue)}</p>
              </div>
            </div>

            {risk.sectorBreakdown.length > 0 && (
              <div className="mt-5 space-y-2">
                <p className="text-xs text-text-muted">Sector breakdown</p>
                {risk.sectorBreakdown.map((sector) => (
                  <div key={sector.sectorName} className="flex items-center gap-3">
                    <span className="w-32 shrink-0 text-text">{sector.sectorName}</span>
                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-bg">
                      <div
                        className="h-full rounded-full bg-accent"
                        style={{ width: `${Math.min(100, sector.percentOfPortfolio)}%` }}
                      />
                    </div>
                    <span className="w-14 shrink-0 text-right text-text-muted">{sector.percentOfPortfolio.toFixed(1)}%</span>
                  </div>
                ))}
              </div>
            )}
          </WidgetCard>
        </div>
      )}
    </div>
  )
}
