const LEVEL_STYLES: Record<string, string> = {
  VERY_LOW: 'bg-gain-soft text-gain',
  LOW: 'bg-gain-soft text-gain',
  MEDIUM: 'bg-neutral-signal-soft text-neutral-signal',
  MODERATE: 'bg-neutral-signal-soft text-neutral-signal',
  HIGH: 'bg-loss-soft text-loss',
  VERY_HIGH: 'bg-loss-soft text-loss',
}

export function LevelBadge({ level }: { level: string | null }) {
  if (!level) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = LEVEL_STYLES[level] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {level.replace('_', ' ')}
    </span>
  )
}
