// Genuinely separate from MaterialityBadge - reportedFlowState is "reported", not "accumulation":
// bulk/block deals are disclosed, qualifying participants only, so this proves the visible deals
// lean buy/sell-side, not genuine accumulation/distribution. Directional (buy=gain, sell=loss),
// unlike materiality's plain importance gradient.
const FLOW_STATE_STYLES: Record<string, string> = {
  STRONG_NET_BUYING: 'bg-gain-soft text-gain',
  NET_BUYING: 'bg-gain-soft text-gain',
  BALANCED: 'bg-neutral-signal-soft text-neutral-signal',
  NET_SELLING: 'bg-loss-soft text-loss',
  STRONG_NET_SELLING: 'bg-loss-soft text-loss',
}

export function FlowStateBadge({ state }: { state: string | null }) {
  if (!state) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = FLOW_STATE_STYLES[state] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {state.replace(/_/g, ' ')}
    </span>
  )
}
