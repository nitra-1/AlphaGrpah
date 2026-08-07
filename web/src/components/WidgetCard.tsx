import type { ReactNode } from 'react'

export function WidgetCard({
  title,
  subtitle,
  isEmpty,
  emptyLabel,
  children,
}: {
  title: string
  subtitle?: string
  isEmpty: boolean
  emptyLabel: string
  children: ReactNode
}) {
  return (
    <div className="rounded-2xl border border-border bg-surface p-5">
      <h2 className="text-sm font-semibold text-text">{title}</h2>
      {subtitle && <p className="mt-0.5 text-xs text-text-muted">{subtitle}</p>}
      <div className="mt-4 text-sm">
        {isEmpty ? <p className="text-text-muted">{emptyLabel}</p> : children}
      </div>
    </div>
  )
}
