import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center bg-bg px-4 text-center">
      <p className="text-sm font-semibold text-accent">404</p>
      <h1 className="mt-2 text-2xl font-bold text-text">Page not found</h1>
      <p className="mt-2 text-sm text-text-muted">The page you're looking for doesn't exist.</p>
      <Link to="/" className="mt-6 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover">
        Back to Rankings
      </Link>
    </div>
  )
}
