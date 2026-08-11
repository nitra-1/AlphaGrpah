import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

const NAV_ITEMS = [
  { to: '/', label: 'Rankings', end: true, adminOnly: false },
  { to: '/dashboard', label: 'Dashboard', adminOnly: false },
  { to: '/watchlist', label: 'Watchlist', adminOnly: false },
  { to: '/portfolio', label: 'Portfolio', adminOnly: false },
  { to: '/compare', label: 'Compare', adminOnly: false },
  { to: '/daily-report', label: 'Daily Report', adminOnly: false },
  { to: '/trade-journal', label: 'Trade Journal', adminOnly: false },
  { to: '/admin/news-review', label: 'News Review', adminOnly: true },
  { to: '/admin/add-instrument', label: 'Add Instrument', adminOnly: true },
  { to: '/admin/sectors', label: 'Sectors', adminOnly: true },
  { to: '/admin/add-financial-data', label: 'Add Financial Data', adminOnly: true },
  { to: '/admin/users', label: 'Add User', adminOnly: true },
]

export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { logout, role } = useAuth()
  const visibleItems = NAV_ITEMS.filter((item) => !item.adminOnly || role === 'ADMIN')

  return (
    <>
      {open && (
        <div className="fixed inset-0 z-20 bg-black/30 md:hidden" onClick={onClose} aria-hidden="true" />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex h-full w-60 shrink-0 flex-col border-r border-border bg-surface transition-transform md:static md:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="px-5 py-6">
          <span className="text-lg font-bold tracking-tight text-text">
            Alpha<span className="text-accent">Graph</span>
          </span>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={onClose}
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
    </>
  )
}
