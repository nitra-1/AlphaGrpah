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

export function ConfirmationBadge({ state }: { state: string | null }) {
  if (!state) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = CONFIRMATION_STYLES[state] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {state.replace(/_/g, ' ')}
    </span>
  )
}
