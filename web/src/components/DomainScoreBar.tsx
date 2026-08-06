function colorFor(value: number): string {
  if (value >= 65) return 'var(--color-gain)'
  if (value >= 40) return 'var(--color-neutral-signal)'
  return 'var(--color-loss)'
}

/** A single domain sub-score (0-100) as a compact labeled bar - null renders as an empty slot rather than a fabricated zero. */
export function DomainScoreBar({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="flex items-center gap-1.5" title={value === null ? `${label}: not available` : `${label}: ${value.toFixed(1)}`}>
      <span className="w-3 text-[10px] font-medium text-text-muted">{label}</span>
      <div className="h-1.5 w-8 overflow-hidden rounded-full bg-border">
        {value !== null && (
          <div className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, value))}%`, background: colorFor(value) }} />
        )}
      </div>
    </div>
  )
}
