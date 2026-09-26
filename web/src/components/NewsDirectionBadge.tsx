const DIRECTION_STYLES: Record<string, string> = {
  POSITIVE: 'bg-gain-soft text-gain',
  NEGATIVE: 'bg-loss-soft text-loss',
  MIXED: 'bg-neutral-signal-soft text-neutral-signal',
  NEUTRAL: 'bg-gray-100 text-text-muted',
}

export function NewsDirectionBadge({ direction }: { direction: string }) {
  const style = DIRECTION_STYLES[direction] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {direction.charAt(0) + direction.slice(1).toLowerCase()}
    </span>
  )
}
