// Deliberately a neutral "how sure is this call" palette, not gain/loss - CONFIRMED doesn't mean
// "bullish" (a CONFIRMED distribution is bad news for holders, but a valid confirmed call).
// FAILED is the one genuinely negative state here - the interpretation itself didn't hold up.
const CONFIRMATION_STYLES: Record<string, string> = {
  PENDING: 'bg-bg text-text-muted',
  PARTIALLY_CONFIRMED: 'bg-neutral-signal-soft text-neutral-signal',
  CONFIRMED: 'bg-accent text-white',
  FAILED: 'bg-loss-soft text-loss',
  NOT_APPLICABLE: 'bg-bg text-text-muted',
}

// LENSKART investigation follow-up: a bare "PENDING" badge tells the user nothing about why it
// isn't resolved yet. The session count (already returned by every endpoint that returns this
// state) is the thing that actually explains it - display-layer only, the underlying
// PENDING/PARTIALLY_CONFIRMED/CONFIRMED/FAILED/NOT_APPLICABLE enum and ladder logic are untouched.
function confirmationLabel(state: string, sessionsElapsed: number): string {
  switch (state) {
    case 'PENDING':
      return `Awaiting confirmation (T+${sessionsElapsed} of 5)`
    case 'PARTIALLY_CONFIRMED':
      return `Partially confirmed (T+${sessionsElapsed} of 5)`
    case 'CONFIRMED':
      return 'Confirmed (T+5)'
    case 'FAILED':
      return `Failed (T+${sessionsElapsed} of 5)`
    default:
      return state.replace(/_/g, ' ')
  }
}

export function ConfirmationBadge({
  state,
  sessionsElapsed = 0,
}: {
  state: string | null
  sessionsElapsed?: number
}) {
  if (!state) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = CONFIRMATION_STYLES[state] ?? 'bg-gray-100 text-text-muted'
  const badge = (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {confirmationLabel(state, sessionsElapsed)}
    </span>
  )
  if (state === 'NOT_APPLICABLE') {
    return badge
  }
  return (
    <span
      className="inline-flex items-center"
      title="Checking price, delivery, and volume behavior over the 5 trading sessions after this signal was detected."
    >
      {badge}
    </span>
  )
}
