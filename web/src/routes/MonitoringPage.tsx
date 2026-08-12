import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { CronStatus, LiveSourceStatus } from '../types/monitoring'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'

const CRON_STATUS_STYLES: Record<string, string> = {
  SUCCESS: 'bg-gain-soft text-gain',
  PARTIAL: 'bg-neutral-signal-soft text-neutral-signal',
  RUNNING: 'bg-neutral-signal-soft text-neutral-signal',
  FAILED: 'bg-loss-soft text-loss',
}

const SOURCE_STATUS_STYLES: Record<string, string> = {
  UP: 'bg-gain-soft text-gain',
  DEGRADED: 'bg-neutral-signal-soft text-neutral-signal',
  DOWN: 'bg-loss-soft text-loss',
  NOT_CONFIGURED: 'bg-gray-100 text-text-muted',
}

function StatusPill({ label, styles }: { label: string; styles: Record<string, string> }) {
  const style = styles[label] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${style}`}>
      {label}
    </span>
  )
}

function formatDateTime(iso: string | null) {
  if (!iso) {
    return '—'
  }
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

export function MonitoringPage() {
  const cronsQuery = useQuery({
    queryKey: ['admin-monitoring-crons'],
    queryFn: () => apiFetch<CronStatus[]>('/admin/monitoring/crons'),
    refetchInterval: 60_000,
  })

  const sourcesQuery = useQuery({
    queryKey: ['admin-monitoring-sources'],
    queryFn: () => apiFetch<LiveSourceStatus[]>('/admin/monitoring/sources'),
    enabled: false,
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Monitoring</h1>
      <p className="mt-1 text-sm text-text-muted">
        Execution status of every cron in the pipeline, and live reachability of every external source the collectors depend on.
      </p>

      <div className="mt-8 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-text">Crons</h2>
        <span className="text-xs text-text-muted">Refreshes automatically every minute</span>
      </div>

      {cronsQuery.isLoading && (
        <div className="mt-4 space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {cronsQuery.error && <ErrorState message="Couldn't load cron status." onRetry={cronsQuery.refetch} />}

      {cronsQuery.data && (
        <div className="mt-4 overflow-x-auto rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase tracking-wide text-text-muted">
                <th className="px-4 py-3 font-medium">Cron</th>
                <th className="px-4 py-3 font-medium">Schedule</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Last Completed</th>
                <th className="px-4 py-3 font-medium">Summary</th>
              </tr>
            </thead>
            <tbody>
              {cronsQuery.data.map((cron) => (
                <tr key={cron.name} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 font-medium text-text">{cron.name}</td>
                  <td className="px-4 py-3 text-text-muted">{cron.schedule}</td>
                  <td className="px-4 py-3">
                    {cron.lastStatus ? (
                      <StatusPill label={cron.lastStatus} styles={CRON_STATUS_STYLES} />
                    ) : (
                      <span className="text-xs text-text-muted">Never run</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-muted">{formatDateTime(cron.lastFinishedAt)}</td>
                  <td className="px-4 py-3 text-text-muted">{cron.lastSummary ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="mt-10 flex items-center justify-between">
        <h2 className="text-lg font-semibold text-text">Live External Sources</h2>
        <button
          className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
          disabled={sourcesQuery.isFetching}
          onClick={() => sourcesQuery.refetch()}
        >
          {sourcesQuery.isFetching ? 'Checking…' : sourcesQuery.data ? 'Re-check now' : 'Check now'}
        </button>
      </div>
      <p className="mt-1 text-sm text-text-muted">
        Checks the exact URLs the collectors call, live, right now - not cached from the last scheduled run.
      </p>

      {sourcesQuery.isFetching && (
        <div className="mt-4 space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      )}

      {sourcesQuery.error && <ErrorState message="Couldn't check live sources." onRetry={sourcesQuery.refetch} />}

      {!sourcesQuery.data && !sourcesQuery.isFetching && !sourcesQuery.error && (
        <p className="mt-4 text-sm text-text-muted">Not checked yet this session - click "Check now" to run a live connectivity check.</p>
      )}

      {sourcesQuery.data && !sourcesQuery.isFetching && (
        <div className="mt-4 overflow-x-auto rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase tracking-wide text-text-muted">
                <th className="px-4 py-3 font-medium">Source</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">HTTP</th>
                <th className="px-4 py-3 font-medium">Latency</th>
                <th className="px-4 py-3 font-medium">Detail</th>
              </tr>
            </thead>
            <tbody>
              {sourcesQuery.data.map((source) => (
                <tr key={source.name} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <div className="font-medium text-text">{source.name}</div>
                    <div className="max-w-md truncate text-xs text-text-muted" title={source.url}>{source.url}</div>
                  </td>
                  <td className="px-4 py-3">
                    <StatusPill label={source.status} styles={SOURCE_STATUS_STYLES} />
                  </td>
                  <td className="px-4 py-3 text-text-muted">{source.httpStatus ?? '—'}</td>
                  <td className="px-4 py-3 text-text-muted">{source.latencyMs} ms</td>
                  <td className="px-4 py-3 text-text-muted">{source.detail ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
