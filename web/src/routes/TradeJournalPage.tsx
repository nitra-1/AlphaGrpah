import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { OutcomeSummary, TradeJournalEntry } from '../types/tradeJournal'
import { StatTile } from '../components/StatTile'
import { WidgetCard } from '../components/WidgetCard'

function formatMoney(value: number | null) {
  if (value == null) return '—'
  const sign = value >= 0 ? '' : '-'
  return `${sign}₹${Math.abs(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function TradeJournalPage() {
  const journalQuery = useQuery({
    queryKey: ['trade-journal'],
    queryFn: () => apiFetch<TradeJournalEntry[]>('/trade-journal'),
  })

  const outcomesQuery = useQuery({
    queryKey: ['trade-journal-outcomes'],
    queryFn: () => apiFetch<OutcomeSummary>('/trade-journal/outcomes'),
  })

  const outcomes = outcomesQuery.data

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Trade Journal</h1>
      <p className="mt-1 text-sm text-text-muted">
        Every buy/sell auto-recorded from the Portfolio (Module 3.8), plus aggregate outcome tracking across closed trades (Module 3.9).
      </p>

      {outcomes && (
        <div className="mt-6">
          <WidgetCard
            title="Outcome Tracking"
            subtitle="Realized P&L across every closed (sold) position"
            isEmpty={outcomes.closedTradeCount === 0}
            emptyLabel="No trades have been closed yet - outcomes appear once you sell a position."
          >
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              <StatTile label="Total Realized P&L" value={formatMoney(outcomes.totalRealizedPnl)} />
              <StatTile label="Closed Trades" value={outcomes.closedTradeCount} />
              <StatTile label="Win Rate" value={outcomes.winRatePercent != null ? `${outcomes.winRatePercent.toFixed(0)}%` : '—'} />
              <StatTile label="Wins / Losses / Breakeven" value={`${outcomes.winCount} / ${outcomes.lossCount} / ${outcomes.breakEvenCount}`} />
              <StatTile label="Average Win" value={formatMoney(outcomes.averageWin)} />
              <StatTile label="Average Loss" value={formatMoney(outcomes.averageLoss)} />
            </div>

            {(outcomes.bestTrade || outcomes.worstTrade) && (
              <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
                {outcomes.bestTrade && (
                  <div className="rounded-xl border border-border bg-bg px-4 py-3">
                    <p className="text-xs text-text-muted">Best Trade</p>
                    <p className="mt-1 text-sm font-semibold text-text">
                      {outcomes.bestTrade.symbol} <span className="text-gain">{formatMoney(outcomes.bestTrade.realizedPnl)}</span>
                    </p>
                  </div>
                )}
                {outcomes.worstTrade && (
                  <div className="rounded-xl border border-border bg-bg px-4 py-3">
                    <p className="text-xs text-text-muted">Worst Trade</p>
                    <p className="mt-1 text-sm font-semibold text-text">
                      {outcomes.worstTrade.symbol} <span className="text-loss">{formatMoney(outcomes.worstTrade.realizedPnl)}</span>
                    </p>
                  </div>
                )}
              </div>
            )}

            {outcomes.byInstrument.length > 0 && (
              <div className="mt-5">
                <p className="text-xs text-text-muted">Per instrument</p>
                <table className="mt-2 w-full text-left text-sm">
                  <thead>
                    <tr className="text-xs text-text-muted">
                      <th className="pb-2 font-medium">Symbol</th>
                      <th className="pb-2 font-medium">Realized P&L</th>
                      <th className="pb-2 font-medium">Closed</th>
                      <th className="pb-2 font-medium">Win Rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {outcomes.byInstrument.map((row) => (
                      <tr key={row.instrumentId} className="border-t border-border">
                        <td className="py-2 font-semibold text-text">{row.symbol}</td>
                        <td className={`py-2 ${row.realizedPnl >= 0 ? 'text-gain' : 'text-loss'}`}>{formatMoney(row.realizedPnl)}</td>
                        <td className="py-2 text-text-muted">{row.closedTradeCount}</td>
                        <td className="py-2 text-text-muted">{row.winRatePercent != null ? `${row.winRatePercent.toFixed(0)}%` : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </WidgetCard>
        </div>
      )}

      {journalQuery.isLoading && <p className="mt-8 text-sm text-text-muted">Loading…</p>}
      {journalQuery.error && <p className="mt-8 text-sm text-loss">Couldn't load the trade journal.</p>}

      {journalQuery.data && journalQuery.data.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">No trades recorded yet - buy or sell a position in Portfolio to see it here.</p>
      )}

      {journalQuery.data && journalQuery.data.length > 0 && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Action</th>
                <th className="px-4 py-3 font-medium">Qty</th>
                <th className="px-4 py-3 font-medium">Price</th>
                <th className="px-4 py-3 font-medium">Trade Value</th>
                <th className="px-4 py-3 font-medium">Cost Basis</th>
                <th className="px-4 py-3 font-medium">Realized P&L</th>
                <th className="px-4 py-3 font-medium">Rationale</th>
              </tr>
            </thead>
            <tbody>
              {journalQuery.data.map((entry, i) => (
                <tr key={i} className="border-b border-border last:border-0 hover:bg-bg">
                  <td className="px-4 py-3 text-text-muted">{formatDateTime(entry.createdAt)}</td>
                  <td className="px-4 py-3 font-semibold text-text">{entry.symbol}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${
                      entry.action === 'BUY' ? 'bg-accent-soft text-accent' : 'bg-neutral-signal-soft text-neutral-signal'
                    }`}>
                      {entry.action}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-text">{entry.quantity}</td>
                  <td className="px-4 py-3 text-text">{formatMoney(entry.price)}</td>
                  <td className="px-4 py-3 text-text">{formatMoney(entry.tradeValue)}</td>
                  <td className="px-4 py-3 text-text-muted">{formatMoney(entry.costBasisPrice)}</td>
                  <td className={`px-4 py-3 font-medium ${entry.realizedPnl == null ? 'text-text-muted' : entry.realizedPnl >= 0 ? 'text-gain' : 'text-loss'}`}>
                    {formatMoney(entry.realizedPnl)}
                  </td>
                  <td className="px-4 py-3 text-text-muted">{entry.rationale ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
