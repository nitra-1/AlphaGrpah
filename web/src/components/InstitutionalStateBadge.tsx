// POSSIBLE_ACCUMULATION/POSSIBLE_DISTRIBUTION are directional (gain/loss); HIGH_CHURN and
// MIXED_ACTIVITY are "noise, not a clear ownership signal" (neutral); NO_CLEAR_SIGNAL is muted.
const INSTITUTIONAL_STATE_STYLES: Record<string, string> = {
  POSSIBLE_ACCUMULATION: 'bg-gain-soft text-gain',
  POSSIBLE_DISTRIBUTION: 'bg-loss-soft text-loss',
  HIGH_CHURN: 'bg-neutral-signal-soft text-neutral-signal',
  MIXED_ACTIVITY: 'bg-neutral-signal-soft text-neutral-signal',
  NO_CLEAR_SIGNAL: 'bg-bg text-text-muted',
}

export function InstitutionalStateBadge({ state }: { state: string | null }) {
  if (!state) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = INSTITUTIONAL_STATE_STYLES[state] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {state.replace(/_/g, ' ')}
    </span>
  )
}
