import { Routes, Route } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './routes/LoginPage'
import { RankingsPage } from './routes/RankingsPage'
import { DashboardPage } from './routes/DashboardPage'
import { WatchlistPage } from './routes/WatchlistPage'
import { PortfolioPage } from './routes/PortfolioPage'
import { ComparePage } from './routes/ComparePage'
import { DailyReportPage } from './routes/DailyReportPage'
import { TradeJournalPage } from './routes/TradeJournalPage'
import { InstrumentDetailPage } from './routes/InstrumentDetailPage'
import { NotFoundPage } from './routes/NotFoundPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<RankingsPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/watchlist" element={<WatchlistPage />} />
          <Route path="/portfolio" element={<PortfolioPage />} />
          <Route path="/compare" element={<ComparePage />} />
          <Route path="/daily-report" element={<DailyReportPage />} />
          <Route path="/trade-journal" element={<TradeJournalPage />} />
          <Route path="/instruments/:instrumentId" element={<InstrumentDetailPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
