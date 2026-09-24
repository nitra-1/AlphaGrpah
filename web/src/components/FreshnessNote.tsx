/** Renders an as-of date plainly, plus an amber "stale" note when it isn't today - so an older valid classification can never visually pass for today's result. Purely a client-side comparison against the browser's own local date; no backend staleness flag exists or is needed. */
export function FreshnessNote({ asOfDate }: { asOfDate: string | null }) {
  if (!asOfDate) {
    return <span className="text-xs text-text-muted">As of —</span>
  }
  const today = new Date().toISOString().slice(0, 10)
  const isStale = asOfDate !== today

  return (
    <span className="inline-flex items-center gap-1 text-xs text-text-muted">
      As of {asOfDate}
      {isStale && (
        <span className="font-semibold text-neutral-signal" title="Not today's result - the underlying pipeline may not have run since this date">
          ⚠ stale
        </span>
      )}
    </span>
  )
}
