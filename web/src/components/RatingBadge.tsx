const RATING_STYLES: Record<string, string> = {
  STRONG_BUY: 'bg-gain-soft text-gain',
  BUY: 'bg-gain-soft text-gain',
  HOLD: 'bg-neutral-signal-soft text-neutral-signal',
  REDUCE: 'bg-loss-soft text-loss',
  AVOID: 'bg-loss-soft text-loss',
}

export function RatingBadge({ rating }: { rating: string | null }) {
  if (!rating) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = RATING_STYLES[rating] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {rating.replace('_', ' ')}
    </span>
  )
}
