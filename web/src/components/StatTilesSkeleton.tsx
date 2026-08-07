import { Skeleton } from './Skeleton'

export function StatTilesSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-xl border border-border bg-bg px-4 py-3">
          <Skeleton className="h-3 w-16" />
          <Skeleton className="mt-2 h-5 w-12" />
        </div>
      ))}
    </div>
  )
}
