const LIFECYCLE_STYLES: Record<string, string> = {
  ACCELERATING: 'bg-gain-soft text-gain',
  MARKET_RECOGNITION: 'bg-gain-soft text-gain',
  MATURE_RERATING: 'bg-gain-soft text-gain',
  EARLY_INFLECTION: 'bg-neutral-signal-soft text-neutral-signal',
  EMERGING: 'bg-neutral-signal-soft text-neutral-signal',
  DETERIORATING: 'bg-loss-soft text-loss',
  DORMANT: 'bg-gray-100 text-text-muted',
}

/** One badge per Stage 5 lifecycle_state (7 values) - null/unclassified renders as a neutral "Insufficient History" note, never a fabricated state. */
export function LifecycleBadge({ state, readiness }: { state: string | null; readiness: string | null }) {
  if (!state) {
    return (
      <span className="inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-text-muted">
        {readiness === 'PARTIAL_HISTORY' ? 'Partial History' : 'Insufficient History'}
      </span>
    )
  }
  const style = LIFECYCLE_STYLES[state] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {state.replace(/_/g, ' ')}
    </span>
  )
}
