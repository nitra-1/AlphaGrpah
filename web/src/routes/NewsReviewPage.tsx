import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { NewsReviewItem } from '../types/newsReview'
import { ErrorState } from '../components/ErrorState'
import { Skeleton } from '../components/Skeleton'

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function NewsReviewPage() {
  const queryClient = useQueryClient()
  const [actioningId, setActioningId] = useState<string | null>(null)

  const listQuery = useQuery({
    queryKey: ['news-review'],
    queryFn: () => apiFetch<NewsReviewItem[]>('/admin/news-review'),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['news-review'] })
  }

  const keepMutation = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/admin/news-review/${id}/keep`, { method: 'POST' }),
    onMutate: (id) => setActioningId(id),
    onSuccess: invalidate,
    onSettled: () => setActioningId(null),
  })

  const discardMutation = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/admin/news-review/${id}/discard`, { method: 'POST' }),
    onMutate: (id) => setActioningId(id),
    onSuccess: invalidate,
    onSettled: () => setActioningId(null),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">News Review</h1>
      <p className="mt-1 text-sm text-text-muted">
        Articles the relevance pre-filter couldn't match to any tracked instrument - review whenever you get to it. Keep sends it for real
        AI extraction right now; Discard drops it for good.
      </p>

      {listQuery.isLoading && (
        <div className="mt-6 space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="rounded-2xl border border-border bg-surface p-5">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="mt-3 h-3 w-full" />
              <Skeleton className="mt-1 h-3 w-5/6" />
            </div>
          ))}
        </div>
      )}

      {listQuery.error && <ErrorState message="Couldn't load pending news reviews." onRetry={listQuery.refetch} />}

      {listQuery.data && listQuery.data.length === 0 && (
        <p className="mt-8 text-sm text-text-muted">Nothing pending review - every recently collected article matched a tracked instrument.</p>
      )}

      {listQuery.data && listQuery.data.length > 0 && (
        <div className="mt-6 space-y-3">
          {listQuery.data.map((item) => {
            const isActioning = actioningId === item.id && (keepMutation.isPending || discardMutation.isPending)
            return (
              <div key={item.id} className="rounded-2xl border border-border bg-surface p-5">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <a href={item.sourceUrl} target="_blank" rel="noreferrer" className="font-semibold text-accent hover:underline">
                      {item.title}
                    </a>
                    <p className="mt-0.5 text-xs text-text-muted">{item.source} &middot; {formatDateTime(item.announcedAt)}</p>
                  </div>
                </div>
                <p className="mt-3 text-sm text-text-muted">{item.extractedText}</p>
                <div className="mt-4 flex items-center gap-3">
                  <button
                    className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
                    disabled={isActioning}
                    onClick={() => keepMutation.mutate(item.id)}
                  >
                    {actioningId === item.id && keepMutation.isPending ? 'Extracting…' : 'Keep'}
                  </button>
                  <button
                    className="rounded-lg border border-border px-4 py-2 text-sm font-semibold text-text-muted hover:bg-bg disabled:opacity-50"
                    disabled={isActioning}
                    onClick={() => discardMutation.mutate(item.id)}
                  >
                    Discard
                  </button>
                  {actioningId === item.id && keepMutation.isPending && (
                    <span className="text-xs text-text-muted">This calls Claude live and can take a few seconds.</span>
                  )}
                </div>
                {actioningId === item.id && (keepMutation.isError || discardMutation.isError) && (
                  <p className="mt-2 text-sm text-loss">Couldn't complete that action.</p>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
