// Correction 4 from plan review: candidates get their own explicit lifecycle (NEW ->
// UNDER_OBSERVATION -> PROMOTED | DISMISSED), distinct from Stage 1-5's own tracked-universe
// evidence chain - this badge is the UI's only place that distinguishes "just detected" from
// "someone's actually watching this."
const STATUS_STYLES: Record<string, string> = {
  NEW: 'bg-neutral-signal-soft text-neutral-signal',
  UNDER_OBSERVATION: 'bg-accent-soft text-accent',
  PROMOTED: 'bg-gain-soft text-gain',
  DISMISSED: 'bg-gray-100 text-text-muted',
}

const STATUS_LABELS: Record<string, string> = {
  NEW: 'New',
  UNDER_OBSERVATION: 'Under Observation',
  PROMOTED: 'Promoted',
  DISMISSED: 'Dismissed',
}

export function NewsCandidateStatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${style}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  )
}
