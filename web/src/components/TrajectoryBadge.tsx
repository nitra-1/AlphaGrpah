const TRAJECTORY_STYLES: Record<string, string> = {
  RISING: 'bg-gain-soft text-gain',
  RECOVERING: 'bg-neutral-signal-soft text-neutral-signal',
  STABLE: 'bg-gray-100 text-text-muted',
  WEAKENING: 'bg-loss-soft text-loss',
  UNKNOWN: 'bg-gray-100 text-text-muted',
}

const TRAJECTORY_ARROWS: Record<string, string> = {
  RISING: '↑',
  RECOVERING: '↗',
  STABLE: '→',
  WEAKENING: '↓',
  UNKNOWN: '—',
}

/** One badge per TrajectoryDirection (5 values including UNKNOWN). */
export function TrajectoryBadge({ direction }: { direction: string | null }) {
  const key = direction ?? 'UNKNOWN'
  const style = TRAJECTORY_STYLES[key] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      <span aria-hidden="true">{TRAJECTORY_ARROWS[key] ?? '—'}</span>
      {key === 'UNKNOWN' ? '—' : key.charAt(0) + key.slice(1).toLowerCase()}
    </span>
  )
}
