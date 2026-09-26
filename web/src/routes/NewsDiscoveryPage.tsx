import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type {
  CompanyExposureDetail,
  EconomicEventDetail,
  EconomicEventSummary,
  NewsDiscoveryCandidate,
  NewsDiscoverySummary,
  SectorImpactMapEntry,
} from '../types/newsDiscovery'
import { NewsDirectionBadge } from '../components/NewsDirectionBadge'
import { NewsCandidateStatusBadge } from '../components/NewsCandidateStatusBadge'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'
import { TableSkeleton } from '../components/TableSkeleton'

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatWords(value: string) {
  return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
}

function delta(current: number, previous: number): { text: string; positive: boolean } | null {
  if (previous === 0 && current === 0) return null
  const diff = current - previous
  return { text: `${diff >= 0 ? '+' : ''}${diff} vs prior day`, positive: diff >= 0 }
}

function StatCard({ label, value, previous }: { label: string; value: number; previous: number }) {
  const d = delta(value, previous)
  return (
    <div className="rounded-2xl border border-border bg-surface p-5">
      <p className="text-xs text-text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold text-text">{value}</p>
      {d && <p className={`mt-1 text-xs ${d.positive ? 'text-gain' : 'text-loss'}`}>{d.text}</p>}
    </div>
  )
}

export function NewsDiscoveryPage() {
  const queryClient = useQueryClient()
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [candidateTab, setCandidateTab] = useState<'ALL' | 'TRACKED' | 'UNTRACKED'>('ALL')
  const [actioningSymbol, setActioningSymbol] = useState<string | null>(null)

  const summaryQuery = useQuery({
    queryKey: ['news-discovery', 'summary'],
    queryFn: () => apiFetch<NewsDiscoverySummary>('/admin/news-discovery/summary'),
  })

  const eventsQuery = useQuery({
    queryKey: ['news-discovery', 'events'],
    queryFn: () => apiFetch<EconomicEventSummary[]>('/admin/news-discovery/events'),
  })

  const sectorMapQuery = useQuery({
    queryKey: ['news-discovery', 'sector-impact-map'],
    queryFn: () => apiFetch<SectorImpactMapEntry[]>('/admin/news-discovery/sector-impact-map'),
  })

  const candidatesQuery = useQuery({
    queryKey: ['news-discovery', 'candidates'],
    queryFn: () => apiFetch<NewsDiscoveryCandidate[]>('/admin/news-discovery/candidates'),
  })

  const observeMutation = useMutation({
    mutationFn: (symbol: string) => apiFetch<void>(`/admin/news-discovery/candidates/${symbol}/observe`, { method: 'POST' }),
    onMutate: (symbol) => setActioningSymbol(symbol),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['news-discovery', 'candidates'] }),
    onSettled: () => setActioningSymbol(null),
  })

  const dismissMutation = useMutation({
    mutationFn: (symbol: string) => apiFetch<void>(`/admin/news-discovery/candidates/${symbol}/dismiss`, { method: 'POST' }),
    onMutate: (symbol) => setActioningSymbol(symbol),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['news-discovery', 'candidates'] }),
    onSettled: () => setActioningSymbol(null),
  })

  const filteredCandidates = (candidatesQuery.data ?? []).filter((c) => {
    if (candidateTab === 'TRACKED') return c.tracked
    if (candidateTab === 'UNTRACKED') return !c.tracked
    return true
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">News & Economic Discovery</h1>
      <p className="mt-1 text-sm text-text-muted">
        Every collected article is automatically classified - Economic Event → Sector Impact → Company Exposure (tracked and untracked) →
        Discovery Candidate. This is evidence and context only; it never modifies the Decision Engine's own six scores, and promotion to a
        tracked instrument only ever happens through the existing Add Instrument flow below.
      </p>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {summaryQuery.isLoading &&
          Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="rounded-2xl border border-border bg-surface p-5">
              <Skeleton className="h-3 w-20" />
              <Skeleton className="mt-2 h-6 w-12" />
            </div>
          ))}
        {summaryQuery.error && <ErrorState message="Couldn't load summary stats." onRetry={summaryQuery.refetch} />}
        {summaryQuery.data && (
          <>
            <StatCard label="Total News Processed" value={summaryQuery.data.totalProcessed} previous={summaryQuery.data.totalProcessedPreviousDay} />
            <StatCard
              label="Economy Relevant"
              value={summaryQuery.data.economyRelevantCount}
              previous={summaryQuery.data.economyRelevantCountPreviousDay}
            />
            <StatCard
              label="Unique Economic Events"
              value={summaryQuery.data.uniqueEconomicEventCount}
              previous={summaryQuery.data.uniqueEconomicEventCountPreviousDay}
            />
            <StatCard
              label="Sectors Impacted"
              value={summaryQuery.data.sectorsImpactedCount}
              previous={summaryQuery.data.sectorsImpactedCountPreviousDay}
            />
            <StatCard
              label="Companies Identified"
              value={summaryQuery.data.companiesIdentifiedTracked + summaryQuery.data.companiesIdentifiedUntracked}
              previous={summaryQuery.data.companiesIdentifiedPreviousDay}
            />
          </>
        )}
      </div>

      <div className="mt-6 grid gap-5 lg:grid-cols-2">
        <div className="rounded-2xl border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-text">Latest Economic Events</h2>
          {eventsQuery.isLoading && <div className="mt-3"><TableSkeleton columns={3} /></div>}
          {eventsQuery.error && <ErrorState message="Couldn't load events." onRetry={eventsQuery.refetch} />}
          {eventsQuery.data && eventsQuery.data.length === 0 && (
            <p className="mt-4 text-sm text-text-muted">No economic events detected yet.</p>
          )}
          {eventsQuery.data && eventsQuery.data.length > 0 && (
            <div className="mt-3 space-y-2">
              {eventsQuery.data.map((event) => (
                <button
                  key={event.id}
                  onClick={() => setSelectedEventId(event.id)}
                  className={`block w-full rounded-xl border px-4 py-3 text-left transition-colors ${
                    selectedEventId === event.id ? 'border-accent bg-accent-soft' : 'border-border bg-bg hover:bg-border/40'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-text">{formatWords(event.theme)}</span>
                    <NewsDirectionBadge direction={event.direction} />
                  </div>
                  <p className="mt-0.5 text-xs text-text-muted">
                    {formatWords(event.magnitude)} magnitude &middot; {event.sourceArticleCount} article{event.sourceArticleCount === 1 ? '' : 's'}{' '}
                    &middot; {formatDateTime(event.publishedAt)}
                  </p>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-border bg-surface p-5">
          <h2 className="text-sm font-semibold text-text">Sector Impact Map</h2>
          <p className="mt-0.5 text-xs text-text-muted">Strongest still-live impact per sector - never averaged across possibly-conflicting events.</p>
          {sectorMapQuery.isLoading && <div className="mt-3"><TableSkeleton columns={3} /></div>}
          {sectorMapQuery.error && <ErrorState message="Couldn't load sector impact map." onRetry={sectorMapQuery.refetch} />}
          {sectorMapQuery.data && sectorMapQuery.data.length === 0 && (
            <p className="mt-4 text-sm text-text-muted">No live sector impacts right now.</p>
          )}
          {sectorMapQuery.data && sectorMapQuery.data.length > 0 && (
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-xs text-text-muted">
                    <th className="py-2 pr-4 font-medium">Sector</th>
                    <th className="py-2 pr-4 font-medium">Impact</th>
                    <th className="py-2 font-medium">Events</th>
                  </tr>
                </thead>
                <tbody>
                  {sectorMapQuery.data.map((entry) => (
                    <tr key={entry.sectorId ?? entry.sectorName} className="border-b border-border last:border-0">
                      <td className="py-2 pr-4 text-text">{entry.sectorName}</td>
                      <td className="py-2 pr-4">
                        <div className="flex items-center gap-2">
                          <NewsDirectionBadge direction={entry.direction} />
                          <span className="text-xs text-text-muted">{formatWords(entry.strength)}</span>
                        </div>
                      </td>
                      <td className="py-2 text-text-muted">{entry.contributingEventCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {selectedEventId && <EventDetailPanel eventId={selectedEventId} onClose={() => setSelectedEventId(null)} />}

      <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-semibold text-text">News-Driven Discovery Candidates</h2>
          <div className="flex gap-1 rounded-lg bg-bg p-1">
            {(['ALL', 'TRACKED', 'UNTRACKED'] as const).map((tab) => (
              <button
                key={tab}
                onClick={() => setCandidateTab(tab)}
                className={`rounded-md px-3 py-1 text-xs font-semibold transition-colors ${
                  candidateTab === tab ? 'bg-surface text-accent shadow-sm' : 'text-text-muted hover:text-text'
                }`}
              >
                {tab.charAt(0) + tab.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>

        {candidatesQuery.isLoading && <div className="mt-4"><TableSkeleton columns={6} /></div>}
        {candidatesQuery.error && <ErrorState message="Couldn't load discovery candidates." onRetry={candidatesQuery.refetch} />}
        {candidatesQuery.data && filteredCandidates.length === 0 && (
          <p className="mt-4 text-sm text-text-muted">No candidates in this view yet.</p>
        )}
        {filteredCandidates.length > 0 && (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-text-muted">
                  <th className="py-2 pr-4 font-medium">Symbol</th>
                  <th className="py-2 pr-4 font-medium">Company</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="py-2 pr-4 font-medium">Best Exposure</th>
                  <th className="py-2 pr-4 font-medium">Exposures</th>
                  <th className="py-2 pr-4 font-medium">Last Seen</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredCandidates.map((candidate) => {
                  const isActioning = actioningSymbol === candidate.symbol && (observeMutation.isPending || dismissMutation.isPending)
                  return (
                    <tr key={candidate.symbol} className="border-b border-border last:border-0">
                      <td className="py-2 pr-4 font-semibold text-text">
                        {candidate.symbol}
                        {candidate.tracked && <span className="ml-1.5 text-[10px] font-medium text-text-muted">tracked</span>}
                      </td>
                      <td className="py-2 pr-4 text-text-muted">{candidate.companyName}</td>
                      <td className="py-2 pr-4"><NewsCandidateStatusBadge status={candidate.status} /></td>
                      <td className="py-2 pr-4">
                        <div className="flex items-center gap-1.5">
                          <NewsDirectionBadge direction={candidate.bestDirection} />
                          <span className="text-xs text-text-muted">{formatWords(candidate.bestExposureType)}</span>
                        </div>
                      </td>
                      <td className="py-2 pr-4 text-text-muted">{candidate.exposureCount}</td>
                      <td className="py-2 pr-4 text-text-muted">{formatDateTime(candidate.lastSeenAt)}</td>
                      <td className="py-2">
                        <div className="flex items-center gap-2">
                          {!candidate.tracked && candidate.status !== 'DISMISSED' && candidate.status !== 'PROMOTED' && (
                            <Link
                              to={`/admin/add-instrument?symbol=${encodeURIComponent(candidate.symbol)}`}
                              className="rounded-lg bg-accent px-3 py-1.5 text-xs font-semibold text-white hover:bg-accent-hover"
                            >
                              Promote
                            </Link>
                          )}
                          {candidate.status === 'NEW' && (
                            <button
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-semibold text-text-muted hover:bg-bg disabled:opacity-50"
                              disabled={isActioning}
                              onClick={() => observeMutation.mutate(candidate.symbol)}
                            >
                              Observe
                            </button>
                          )}
                          {candidate.status !== 'DISMISSED' && candidate.status !== 'PROMOTED' && (
                            <button
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-semibold text-text-muted hover:bg-bg disabled:opacity-50"
                              disabled={isActioning}
                              onClick={() => dismissMutation.mutate(candidate.symbol)}
                            >
                              Dismiss
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

const EXPOSURE_TABS = ['NEWS_IMPACT', 'SECTOR_IMPACT', 'COMPANY_EXPOSURE'] as const
type ExposureTab = (typeof EXPOSURE_TABS)[number]

const EXPOSURE_TAB_LABELS: Record<ExposureTab, string> = {
  NEWS_IMPACT: 'News Impact',
  SECTOR_IMPACT: 'Sector Impact',
  COMPANY_EXPOSURE: 'Company Exposure',
}

function EventDetailPanel({ eventId, onClose }: { eventId: string; onClose: () => void }) {
  const [tab, setTab] = useState<ExposureTab>('NEWS_IMPACT')

  const detailQuery = useQuery({
    queryKey: ['news-discovery', 'events', eventId],
    queryFn: () => apiFetch<EconomicEventDetail>(`/admin/news-discovery/events/${eventId}`),
  })

  return (
    <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-text">Event Detail</h2>
        <button className="text-sm font-semibold text-accent hover:text-accent-hover" onClick={onClose}>Close</button>
      </div>

      {detailQuery.isLoading && <div className="mt-4"><Skeleton className="h-4 w-2/3" /><Skeleton className="mt-2 h-4 w-1/2" /></div>}
      {detailQuery.error && <ErrorState message="Couldn't load event detail." onRetry={detailQuery.refetch} />}

      {detailQuery.data && (
        <div className="mt-4 grid gap-5 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-lg font-semibold text-text">{formatWords(detailQuery.data.theme)}</span>
              <NewsDirectionBadge direction={detailQuery.data.direction} />
            </div>

            <div className="mt-4 flex flex-wrap gap-2">
              {EXPOSURE_TABS.map((t) => (
                <button
                  key={t}
                  onClick={() => setTab(t)}
                  className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                    tab === t ? 'bg-accent-soft text-accent' : 'bg-bg text-text-muted hover:text-text'
                  }`}
                >
                  {EXPOSURE_TAB_LABELS[t]}
                </button>
              ))}
            </div>

            <div className="mt-4">
              {tab === 'NEWS_IMPACT' && (
                <div className="space-y-2">
                  {detailQuery.data.sourceArticles.length === 0 && <p className="text-sm text-text-muted">No source articles recorded.</p>}
                  {detailQuery.data.sourceArticles.map((article) => (
                    <a
                      key={article.documentId}
                      href={article.sourceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="block rounded-xl border border-border bg-bg px-4 py-3 hover:bg-border/30"
                    >
                      <p className="text-sm font-semibold text-accent">{article.title ?? article.sourceUrl}</p>
                      <p className="mt-0.5 text-xs text-text-muted">{formatDateTime(article.announcedAt)}</p>
                    </a>
                  ))}
                </div>
              )}

              {tab === 'SECTOR_IMPACT' && (
                <div className="space-y-2">
                  {detailQuery.data.sectorImpacts.length === 0 && <p className="text-sm text-text-muted">No sector impacts recorded.</p>}
                  {detailQuery.data.sectorImpacts.map((impact, i) => (
                    <div key={i} className="rounded-xl border border-border bg-bg px-4 py-3">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-semibold text-text">{impact.sectorName}</span>
                        <NewsDirectionBadge direction={impact.direction} />
                      </div>
                      <p className="mt-0.5 text-xs text-text-muted">
                        {formatWords(impact.strength)} strength &middot; confidence {impact.confidence.toFixed(0)}%
                        {impact.mechanism ? ` — ${impact.mechanism}` : ''}
                      </p>
                    </div>
                  ))}
                </div>
              )}

              {tab === 'COMPANY_EXPOSURE' && (
                <div className="space-y-2">
                  {detailQuery.data.companyExposures.length === 0 && <p className="text-sm text-text-muted">No company exposures recorded.</p>}
                  {detailQuery.data.companyExposures.map((exposure, i) => (
                    <CompanyExposureRow key={i} exposure={exposure} />
                  ))}
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="rounded-xl border border-border bg-bg p-4">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Key Details</h3>
              <dl className="mt-2 space-y-1.5 text-sm">
                <div className="flex justify-between"><dt className="text-text-muted">Relevance</dt><dd className="text-text">{formatWords(detailQuery.data.economicRelevance)}</dd></div>
                <div className="flex justify-between"><dt className="text-text-muted">Magnitude</dt><dd className="text-text">{formatWords(detailQuery.data.magnitude)}</dd></div>
                <div className="flex justify-between"><dt className="text-text-muted">Horizon</dt><dd className="text-text">{formatWords(detailQuery.data.horizon)}</dd></div>
                <div className="flex justify-between"><dt className="text-text-muted">Confidence</dt><dd className="text-text">{detailQuery.data.confidence.toFixed(0)}%</dd></div>
                <div className="flex justify-between"><dt className="text-text-muted">Detected</dt><dd className="text-text">{formatDateTime(detailQuery.data.computedAt)}</dd></div>
              </dl>
            </div>
            <div className="mt-3 rounded-xl border border-border bg-bg p-4">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-text-muted">Discovery Actions</h3>
              <p className="mt-2 text-xs text-text-muted">
                Untracked companies exposed to this event appear in the Discovery Candidates table below - promote from there through the
                normal Add Instrument flow.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function CompanyExposureRow({ exposure }: { exposure: CompanyExposureDetail }) {
  return (
    <div className="rounded-xl border border-border bg-bg px-4 py-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-text">{exposure.matchedSymbol ?? exposure.companyNameRaw}</span>
          {exposure.tracked && <span className="text-[10px] font-medium text-text-muted">tracked</span>}
          {!exposure.tracked && exposure.matchType !== 'UNRESOLVED' && <span className="text-[10px] font-medium text-text-muted">untracked</span>}
          {exposure.matchType === 'UNRESOLVED' && (
            <span
              className="text-[10px] font-medium text-text-muted"
              title="Named by the article but couldn't be safely resolved to a real NSE-listed company - never guessed."
            >
              unresolved
            </span>
          )}
        </div>
        <NewsDirectionBadge direction={exposure.direction} />
      </div>
      <p className="mt-0.5 text-xs text-text-muted">
        {formatWords(exposure.exposureType)} &middot; {formatWords(exposure.impactStrength)} impact &middot; confidence{' '}
        {exposure.confidence.toFixed(0)}%
        {exposure.reason ? ` — ${exposure.reason}` : ''}
      </p>
    </div>
  )
}
