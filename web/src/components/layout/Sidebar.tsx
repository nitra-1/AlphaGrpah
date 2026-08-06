import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

const NAV_ITEMS = [
  { to: '/', label: 'Rankings', end: true },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/watchlist', label: 'Watchlist' },
  { to: '/portfolio', label: 'Portfolio' },
  { to: '/compare', label: 'Compare' },
  { to: '/daily-report', label: 'Daily Report' },
  { to: '/trade-journal', label: 'Trade Journal' },
]

export function Sidebar() {
  const { logout } = useAuth()

  return (
    <aside className="flex h-full w-60 shrink-0 flex-col border-r border-border bg-surface">
      <div className="px-5 py-6">
        <span className="text-lg font-bold tracking-tight text-text">
          Alpha<span className="text-accent">Graph</span>
        </span>
      </div>
      <nav className="flex-1 space-y-1 px-3">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                isActive ? 'bg-accent-soft text-accent' : 'text-text-muted hover:bg-bg hover:text-text'
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-border px-3 py-4">
        <button
          onClick={logout}
          className="w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-text-muted transition-colors hover:bg-bg hover:text-text"
        >
          Log out
        </button>
      </div>
    </aside>
  )
}
