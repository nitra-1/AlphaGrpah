export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="mt-8 flex items-center gap-3 rounded-xl border border-loss-soft bg-loss-soft px-4 py-3">
      <p className="text-sm text-loss">{message}</p>
      <button onClick={onRetry} className="text-sm font-semibold text-loss underline hover:no-underline">
        Try again
      </button>
    </div>
  )
}
