const SIGNAL_STYLES: Record<string, string> = {
  POSITIVE: 'bg-gain-soft text-gain',
  NEGATIVE: 'bg-loss-soft text-loss',
  NEUTRAL: 'bg-neutral-signal-soft text-neutral-signal',
  UP: 'bg-gain-soft text-gain',
  DOWN: 'bg-loss-soft text-loss',
}

export function SignalBadge({ signal }: { signal: string }) {
  const style = SIGNAL_STYLES[signal.toUpperCase()] ?? 'bg-gray-100 text-text-muted'
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${style}`}>
      {signal.replace('_', ' ')}
    </span>
  )
}
