const HEALTH_STYLES: Record<string, string> = {
  STRONG: 'bg-gain-soft text-gain',
  STABLE: 'bg-neutral-signal-soft text-neutral-signal',
  WEAKENING: 'bg-loss-soft text-loss',
  SWING_SETUP_BROKEN: 'bg-loss-soft text-loss',
}

export function PositionHealthBadge({ health }: { health: string | null }) {
  if (!health) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = HEALTH_STYLES[health] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {health.replace(/_/g, ' ')}
    </span>
  )
}
