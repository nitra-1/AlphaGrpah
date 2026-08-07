import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiFetch, ApiError } from '../lib/api'
import type { DailyReport } from '../types/dailyReport'
import { StatTile } from '../components/StatTile'

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function DailyReportPage() {
  const [selectedDate, setSelectedDate] = useState('')
  const [pendingDate, setPendingDate] = useState('')

  const reportQuery = useQuery({
    queryKey: ['daily-report', selectedDate || 'latest'],
    queryFn: () => apiFetch<DailyReport>(selectedDate ? `/daily-report/${selectedDate}` : '/daily-report'),
    retry: false,
  })

  const notFound = reportQuery.error instanceof ApiError && reportQuery.error.status === 404
  const otherError = reportQuery.error && !notFound

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Daily Report</h1>
      <p className="mt-1 text-sm text-text-muted">
        A once-daily AI-narrated digest across all tracked instruments, generated at 22:00 IST (Module 3.6).
      </p>

      <div className="mt-6 flex items-center gap-3">
        <input
          type="date"
          className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
          value={pendingDate}
          onChange={(e) => setPendingDate(e.target.value)}
        />
        <button
          className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold text-text hover:bg-bg disabled:opacity-50"
          disabled={!pendingDate}
          onClick={() => setSelectedDate(pendingDate)}
        >
          View
        </button>
        {selectedDate && (
          <button
            className="text-sm font-semibold text-accent hover:underline"
            onClick={() => {
              setSelectedDate('')
              setPendingDate('')
            }}
          >
            Back to latest
          </button>
        )}
      </div>

      {reportQuery.isLoading && <p className="mt-8 text-sm text-text-muted">Loading…</p>}

      {notFound && (
        <p className="mt-8 text-sm text-text-muted">
          {selectedDate ? `No daily report was generated for ${selectedDate}.` : 'No daily report has been generated yet - the first one lands after the 22:00 IST run.'}
        </p>
      )}

      {otherError && <p className="mt-8 text-sm text-loss">Couldn't load the daily report.</p>}

      {reportQuery.data && (
        <div className="mt-6 rounded-2xl border border-border bg-surface p-6">
          <div className="flex items-baseline justify-between">
            <h2 className="text-lg font-semibold text-text">{formatDate(reportQuery.data.reportDate)}</h2>
            <span className="text-xs text-text-muted">Generated {formatDateTime(reportQuery.data.generatedAt)}</span>
          </div>

          <p className="mt-4 whitespace-pre-line text-sm leading-relaxed text-text">{reportQuery.data.narrative}</p>

          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <StatTile
              label="Top Gainer"
              value={reportQuery.data.topGainerSymbol ? `${reportQuery.data.topGainerSymbol} +${reportQuery.data.topGainerRankImprovement}` : '—'}
            />
            <StatTile
              label="Top Decliner"
              value={reportQuery.data.topDeclinerSymbol ? `${reportQuery.data.topDeclinerSymbol} -${reportQuery.data.topDeclinerRankDecline}` : '—'}
            />
            <StatTile label="New Events" value={reportQuery.data.newEventCount} />
            <StatTile label="Guidance Changes" value={reportQuery.data.guidanceChangeCount} />
            <StatTile label="Positive News" value={reportQuery.data.positiveNewsCount} />
            <StatTile label="Negative News" value={reportQuery.data.negativeNewsCount} />
          </div>
        </div>
      )}
    </div>
  )
}
