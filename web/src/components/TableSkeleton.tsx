import { Skeleton } from './Skeleton'

/** Skeleton rows for a data table while its query is loading - matches the real table's border/padding rhythm so the swap-in doesn't jump. */
export function TableSkeleton({ columns, rows = 5 }: { columns: number; rows?: number }) {
  return (
    <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
      <table className="w-full text-left text-sm">
        <tbody>
          {Array.from({ length: rows }).map((_, r) => (
            <tr key={r} className="border-b border-border last:border-0">
              {Array.from({ length: columns }).map((_, c) => (
                <td key={c} className="px-4 py-3">
                  <Skeleton className="h-4 w-full max-w-24" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
