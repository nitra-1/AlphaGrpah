import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { OpportunityDetail, OpportunityDomainDetail } from '../types/opportunities'
import { LifecycleBadge } from '../components/LifecycleBadge'
import { TrajectoryBadge } from '../components/TrajectoryBadge'
import { FreshnessNote } from '../components/FreshnessNote'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'

const DOMAIN_LABELS: Record<string, string> = {
  FINANCIAL: 'Financial',
  OWNERSHIP: 'Ownership',
  MARKET: 'Market',
  SECTOR: 'Sector',
  CAPITAL_ALLOCATION: 'Capital Allocation',
}

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-bg px-4 py-3">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold text-text">{value}</p>
    </div>
  )
}

function LifecycleSummaryCard({ detail }: { detail: OpportunityDetail }) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-text">Lifecycle Summary <span className="text-text-muted font-normal">· Stage 5</span></h2>
        <FreshnessNote asOfDate={detail.lifecycleAsOfDate} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <LifecycleBadge state={detail.lifecycleState} readiness={detail.lifecycleReadiness} />
        <TrajectoryBadge direction={detail.trajectoryDirection} />
        {detail.peakLifecycleStage && (
          <span className="text-xs text-text-muted">Peak: {detail.peakLifecycleStage.replace(/_/g, ' ')}</span>
        )}
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatTile label="Lifecycle Strength" value={detail.lifecycleStrength?.toFixed(1) ?? '—'} />
        <StatTile label="Trajectory Score" value={detail.trajectoryScore?.toFixed(1) ?? '—'} />
        <StatTile label="State Started" value={detail.stateStartedDate ?? '—'} />
        <StatTile label="Lifecycle Age (days)" value={detail.lifecycleAgeDays?.toString() ?? '—'} />
      </div>
      {detail.lifecycleReasons.length > 0 && (
        <ul className="mt-4 space-y-1 text-xs text-text-muted">
          {detail.lifecycleReasons.map((r, i) => (
            <li key={i}>· {r.reasonCode.replace(/_/g, ' ')}{r.metricValue != null ? ` (${r.metricValue})` : ''}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

function ConvergenceCard({ detail }: { detail: OpportunityDetail }) {
  return (
    <div className="mt-5 rounded-2xl border border-border bg-surface p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-text">Cross-Domain Convergence <span className="text-text-muted font-normal">· Stage 4</span></h2>
        <FreshnessNote asOfDate={detail.convergenceAsOfDate} />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <span className="text-sm font-semibold text-text">{detail.convergenceState?.replace(/_/g, ' ') ?? 'No data yet'}</span>
        {detail.activeDomainCount != null && (
          <span className="text-xs text-text-muted">{detail.activeDomainCount} of 5 domains active · {detail.domainCoveragePct ?? 0}% coverage</span>
        )}
      </div>
      {detail.domainContributions.length > 0 && (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-text-muted">
                <th className="py-2 pr-4 font-medium">Domain</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 pr-4 font-medium">Phase</th>
                <th className="py-2 pr-4 font-medium">Strength</th>
                <th className="py-2 font-medium">Contribution</th>
              </tr>
            </thead>
            <tbody>
              {detail.domainContributions.map((c) => (
                <tr key={c.domain} className="border-b border-border last:border-b-0">
                  <td className="py-2 pr-4 text-text">{DOMAIN_LABELS[c.domain] ?? c.domain}</td>
                  <td className="py-2 pr-4 text-text-muted">{c.contributionStatus.replace(/_/g, ' ')}</td>
                  <td className="py-2 pr-4 text-text-muted">{c.strongestPhase ?? '—'}</td>
                  <td className="py-2 pr-4 text-text">{c.domainStrength?.toFixed(1) ?? '—'}</td>
                  <td className="py-2 text-text">{c.contributionScore?.toFixed(1) ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function DomainChainSection({ domain }: { domain: OpportunityDomainDetail }) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Stage 1 · Evidence</h3>
      {domain.evidence.length === 0 ? (
        <p className="mt-2 text-sm text-text-muted">No evidence recorded yet for this domain.</p>
      ) : (
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-text-muted">
                <th className="py-2 pr-4 font-medium">Metric</th>
                <th className="py-2 pr-4 font-medium">As Of</th>
                <th className="py-2 pr-4 font-medium">Value</th>
                <th className="py-2 pr-4 font-medium">Change</th>
                <th className="py-2 font-medium">Confidence</th>
              </tr>
            </thead>
            <tbody>
              {domain.evidence.map((e) => (
                <tr key={e.metricName} className="border-b border-border last:border-b-0">
                  <td className="py-2 pr-4 text-text">{e.metricName.replace(/_/g, ' ')}</td>
                  <td className="py-2 pr-4 text-text-muted">{e.asOfDate}</td>
                  <td className="py-2 pr-4 text-text">{e.value?.toFixed(2) ?? '—'}</td>
                  <td className="py-2 pr-4 text-text-muted">{e.change?.toFixed(2) ?? '—'}</td>
                  <td className="py-2 text-text-muted">{e.confidence?.toFixed(0) ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-text-muted">Stage 2 · Inflection</h3>
      {!domain.inflection ? (
        <p className="mt-2 text-sm text-text-muted">No inflection state recorded yet for this domain.</p>
      ) : (
        <div className="mt-2 rounded-xl border border-border bg-bg px-4 py-3">
          <p className="text-sm font-semibold text-text">{domain.inflection.primaryState.replace(/_/g, ' ')}</p>
          <p className="mt-0.5 text-xs text-text-muted">
            {domain.inflection.drivingMetric ? `${domain.inflection.drivingMetric.replace(/_/g, ' ')} · ` : ''}
            As of {domain.inflection.asOfDate}
            {domain.inflection.persistence != null ? ` · persisted ${domain.inflection.persistence} periods` : ''}
          </p>
          {domain.inflection.reasons.length > 0 && (
            <ul className="mt-2 space-y-0.5 text-xs text-text-muted">
              {domain.inflection.reasons.map((r, i) => (
                <li key={i}>· {r.reasonCode.replace(/_/g, ' ')}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-text-muted">Stage 3 · Transformation Sequences</h3>
      {domain.sequences.length === 0 ? (
        <p className="mt-2 text-sm text-text-muted">No active sequence for this domain as of this date.</p>
      ) : (
        <div className="mt-2 space-y-2">
          {domain.sequences.map((s) => (
            <div key={s.sequenceType} className="rounded-xl border border-border bg-bg px-4 py-3">
              <div className="flex items-center justify-between">
                <p className="text-sm font-semibold text-text">{s.sequenceType.replace(/_/g, ' ')}</p>
                <span className="text-xs font-medium text-text-muted">{s.sequencePhase}</span>
              </div>
              <p className="mt-0.5 text-xs text-text-muted">
                Step {s.currentStep}/{s.totalSteps} · Strength {s.sequenceStrength?.toFixed(1) ?? '—'} · Confidence {s.confidence?.toFixed(0) ?? '—'}
              </p>
            </div>
          ))}
        </div>
      )}

      <h3 className="mt-5 text-xs font-semibold uppercase tracking-wide text-text-muted">Stage 4 · Convergence Contribution</h3>
      {!domain.convergenceContribution ? (
        <p className="mt-2 text-sm text-text-muted">No contribution recorded.</p>
      ) : (
        <p className="mt-2 text-sm text-text">
          {domain.convergenceContribution.contributionStatus.replace(/_/g, ' ')} · Contribution {domain.convergenceContribution.contributionScore?.toFixed(1) ?? '—'}
        </p>
      )}
    </div>
  )
}

function TransformationEvidenceCard({ detail }: { detail: OpportunityDetail }) {
  const [activeDomain, setActiveDomain] = useState(detail.domainDetails[0]?.domain ?? 'FINANCIAL')
  const selected = detail.domainDetails.find((d) => d.domain === activeDomain) ?? detail.domainDetails[0]

  return (
    <div className="mt-5 rounded-2xl border border-border bg-surface p-5">
      <h2 className="text-sm font-semibold text-text">Transformation Evidence <span className="text-text-muted font-normal">· Stage 1-3</span></h2>
      <p className="mt-0.5 text-xs text-text-muted">
        Why each domain became active - evidence → inflection → sequence, resolved as of the convergence snapshot above.
      </p>

      <div className="mt-4 flex flex-wrap gap-2">
        {detail.domainDetails.map((d) => (
          <button
            key={d.domain}
            onClick={() => setActiveDomain(d.domain)}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
              d.domain === selected?.domain ? 'bg-accent-soft text-accent' : 'bg-bg text-text-muted hover:text-text'
            }`}
          >
            {DOMAIN_LABELS[d.domain] ?? d.domain}
          </button>
        ))}
      </div>

      {selected && (
        <div className="mt-5">
          <DomainChainSection domain={selected} />
        </div>
      )}
    </div>
  )
}

function TransitionsCard({ detail }: { detail: OpportunityDetail }) {
  return (
    <div className="mt-5 rounded-2xl border border-border bg-surface p-5">
      <h2 className="text-sm font-semibold text-text">Transition History <span className="text-text-muted font-normal">· Stage 5</span></h2>
      {detail.transitions.length === 0 ? (
        <p className="mt-2 text-sm text-text-muted">No lifecycle transitions recorded yet.</p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs text-text-muted">
                <th className="py-2 pr-4 font-medium">Date</th>
                <th className="py-2 pr-4 font-medium">Change</th>
                <th className="py-2 font-medium">Trigger</th>
              </tr>
            </thead>
            <tbody>
              {detail.transitions.map((t, i) => (
                <tr key={i} className="border-b border-border last:border-b-0">
                  <td className="py-2 pr-4 text-text-muted">{t.transitionDate}</td>
                  <td className="py-2 pr-4 text-text">
                    {(t.fromState ?? 'None').replace(/_/g, ' ')} → {t.toState.replace(/_/g, ' ')}
                  </td>
                  <td className="py-2 text-text-muted">{t.triggerReason.replace(/_/g, ' ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export function OpportunityDetailPage() {
  const { instrumentId } = useParams<{ instrumentId: string }>()

  const query = useQuery({
    queryKey: ['opportunity', instrumentId],
    queryFn: () => apiFetch<OpportunityDetail>(`/opportunities/${instrumentId}`),
  })

  if (query.isLoading) {
    return (
      <div>
        <Skeleton className="h-4 w-32" />
        <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-4 h-8 w-64" />
        </div>
      </div>
    )
  }

  if (query.error) {
    return (
      <div>
        <Link to="/opportunities" className="text-sm font-semibold text-accent hover:underline">← Back to Opportunities</Link>
        <ErrorState message="Couldn't load this opportunity." onRetry={query.refetch} />
      </div>
    )
  }

  if (!query.data) {
    return null
  }

  const detail = query.data

  return (
    <div>
      <Link to="/opportunities" className="text-sm font-semibold text-accent hover:underline">← Back to Opportunities</Link>

      <div className="mt-3">
        <h1 className="text-2xl font-bold text-text">{detail.symbol}</h1>
      </div>

      <div className="mt-5">
        <LifecycleSummaryCard detail={detail} />
      </div>
      <ConvergenceCard detail={detail} />
      <TransformationEvidenceCard detail={detail} />
      <TransitionsCard detail={detail} />
    </div>
  )
}
