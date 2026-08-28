// Deliberately not LevelBadge - that component's LOW=good/HIGH=bad color semantics fit risk
// levels, not materiality: a HIGH/VERY_HIGH materiality deal is significant/noteworthy, not
// "bad". This uses a plain importance gradient instead (muted -> amber -> accent).
const MATERIALITY_STYLES: Record<string, string> = {
  LOW: 'bg-bg text-text-muted',
  MEDIUM: 'bg-neutral-signal-soft text-neutral-signal',
  HIGH: 'bg-accent-soft text-accent',
  VERY_HIGH: 'bg-accent text-white',
}

export function MaterialityBadge({ level }: { level: string | null }) {
  if (!level) {
    return <span className="text-text-muted text-sm">—</span>
  }
  const style = MATERIALITY_STYLES[level] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {level.replace('_', ' ')}
    </span>
  )
}
