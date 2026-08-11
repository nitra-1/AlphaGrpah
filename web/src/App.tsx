import { Routes, Route } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AdminRoute } from './components/AdminRoute'
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
import { NewsReviewPage } from './routes/NewsReviewPage'
import { AddInstrumentPage } from './routes/AddInstrumentPage'
import { SectorsPage } from './routes/SectorsPage'
import { AddFinancialDataPage } from './routes/AddFinancialDataPage'
import { AddUserPage } from './routes/AddUserPage'

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

          <Route element={<AdminRoute />}>
            <Route path="/admin/news-review" element={<NewsReviewPage />} />
            <Route path="/admin/add-instrument" element={<AddInstrumentPage />} />
            <Route path="/admin/sectors" element={<SectorsPage />} />
            <Route path="/admin/add-financial-data" element={<AddFinancialDataPage />} />
            <Route path="/admin/users" element={<AddUserPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
