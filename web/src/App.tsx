import { Routes, Route } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ComingSoon } from './components/ComingSoon'
import { LoginPage } from './routes/LoginPage'
import { RankingsPage } from './routes/RankingsPage'
import { DashboardPage } from './routes/DashboardPage'
import { WatchlistPage } from './routes/WatchlistPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<RankingsPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/watchlist" element={<WatchlistPage />} />
          <Route path="/portfolio" element={<ComingSoon title="Portfolio" />} />
          <Route path="/compare" element={<ComingSoon title="Compare" />} />
          <Route path="/daily-report" element={<ComingSoon title="Daily Report" />} />
          <Route path="/trade-journal" element={<ComingSoon title="Trade Journal" />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
