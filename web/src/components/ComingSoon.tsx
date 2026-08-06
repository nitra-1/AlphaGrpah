export function ComingSoon({ title }: { title: string }) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-text">{title}</h1>
      <p className="mt-2 text-text-muted">This page is built on the backend already — the UI for it is coming next.</p>
    </div>
  )
}
