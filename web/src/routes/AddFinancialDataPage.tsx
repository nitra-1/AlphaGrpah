import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { TrackedInstrument, AddFinancialResultRequest, AddShareholdingRequest } from '../types/financialData'
import { ErrorState } from '../components/ErrorState'

type FieldMap = Record<string, string>

function NumField({
  label, field, values, onChange, required = false, hint,
}: {
  label: string
  field: string
  values: FieldMap
  onChange: (field: string, value: string) => void
  required?: boolean
  hint?: string
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-text-muted">
        {label}
        {required && <span className="text-loss"> *</span>}
      </label>
      <input
        type="number"
        step="any"
        value={values[field] ?? ''}
        onChange={(e) => onChange(field, e.target.value)}
        placeholder={hint ?? 'leave blank if unknown'}
        className="mt-1 w-full rounded-lg border border-border bg-bg px-2.5 py-1.5 text-sm text-text focus:border-accent focus:outline-none"
      />
    </div>
  )
}

function toNumber(value: string | undefined): number | undefined {
  if (value === undefined || value.trim() === '') return undefined
  const n = Number(value)
  return Number.isNaN(n) ? undefined : n
}

export function AddFinancialDataPage() {
  const queryClient = useQueryClient()
  const [selectedSymbol, setSelectedSymbol] = useState('')
  const [financialFields, setFinancialFields] = useState<FieldMap>({ periodType: 'QUARTERLY' })
  const [shareholdingFields, setShareholdingFields] = useState<FieldMap>({})
  const [financialMessage, setFinancialMessage] = useState<string | null>(null)
  const [shareholdingMessage, setShareholdingMessage] = useState<string | null>(null)

  const instrumentsQuery = useQuery({
    queryKey: ['tracked-instruments'],
    queryFn: () => apiFetch<TrackedInstrument[]>('/admin/instruments'),
  })

  const financialMutation = useMutation({
    mutationFn: (body: AddFinancialResultRequest) =>
      apiFetch<void>('/admin/financial-results', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => {
      setFinancialMessage(`Financial results saved for ${selectedSymbol}.`)
      setFinancialFields({ periodType: 'QUARTERLY' })
      queryClient.invalidateQueries({ queryKey: ['rankings'] })
    },
  })

  const shareholdingMutation = useMutation({
    mutationFn: (body: AddShareholdingRequest) =>
      apiFetch<void>('/admin/shareholding-pattern', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => {
      setShareholdingMessage(`Shareholding pattern saved for ${selectedSymbol}.`)
      setShareholdingFields({})
      queryClient.invalidateQueries({ queryKey: ['rankings'] })
    },
  })

  function submitFinancial() {
    setFinancialMessage(null)
    const sales = toNumber(financialFields.sales)
    const pat = toNumber(financialFields.pat)
    if (!selectedSymbol || !financialFields.periodEnd || sales === undefined || pat === undefined) return

    financialMutation.mutate({
      symbol: selectedSymbol,
      periodEnd: financialFields.periodEnd,
      periodType: (financialFields.periodType as 'QUARTERLY' | 'ANNUAL') ?? 'QUARTERLY',
      sales, pat,
      eps: toNumber(financialFields.eps),
      roePercentage: toNumber(financialFields.roePercentage),
      rocePercentage: toNumber(financialFields.rocePercentage),
      operatingMarginPercentage: toNumber(financialFields.operatingMarginPercentage),
      netMarginPercentage: toNumber(financialFields.netMarginPercentage),
      cashFlowFromOperations: toNumber(financialFields.cashFlowFromOperations),
      totalAssets: toNumber(financialFields.totalAssets),
      currentAssets: toNumber(financialFields.currentAssets),
      currentLiabilities: toNumber(financialFields.currentLiabilities),
      totalDebt: toNumber(financialFields.totalDebt),
      totalEquity: toNumber(financialFields.totalEquity),
      interestExpense: toNumber(financialFields.interestExpense),
      ebit: toNumber(financialFields.ebit),
    })
  }

  function submitShareholding() {
    setShareholdingMessage(null)
    const promoterPercentage = toNumber(shareholdingFields.promoterPercentage)
    const fiiPercentage = toNumber(shareholdingFields.fiiPercentage)
    const diiPercentage = toNumber(shareholdingFields.diiPercentage)
    if (!selectedSymbol || !shareholdingFields.periodEnd || promoterPercentage === undefined || fiiPercentage === undefined || diiPercentage === undefined) return

    shareholdingMutation.mutate({
      symbol: selectedSymbol,
      periodEnd: shareholdingFields.periodEnd,
      promoterPercentage, fiiPercentage, diiPercentage,
      mfPercentage: toNumber(shareholdingFields.mfPercentage),
      publicPercentage: toNumber(shareholdingFields.publicPercentage),
    })
  }

  const financialField = (field: string, value: string) => setFinancialFields((f) => ({ ...f, [field]: value }))
  const shareholdingField = (field: string, value: string) => setShareholdingFields((f) => ({ ...f, [field]: value }))

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-text">Add Financial / Shareholding Data</h1>
      <p className="mt-1 text-sm text-text-muted">
        Look up the numbers yourself (e.g. screener.in's quarterly results and shareholding pages) and enter what you find.
        Leave a field blank if you can't find it or aren't sure - never guess a number.
      </p>

      <div className="mt-6 rounded-2xl border border-border bg-surface p-6">
        <label className="block text-sm font-medium text-text">Instrument</label>
        {instrumentsQuery.error && <ErrorState message="Couldn't load tracked instruments." onRetry={instrumentsQuery.refetch} />}
        <select
          value={selectedSymbol}
          onChange={(e) => setSelectedSymbol(e.target.value)}
          className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
        >
          <option value="">Select a tracked instrument…</option>
          {instrumentsQuery.data?.map((i) => (
            <option key={i.id} value={i.symbol}>
              {i.symbol} — {i.name}
            </option>
          ))}
        </select>
      </div>

      {selectedSymbol && (
        <>
          <div className="mt-6 rounded-2xl border border-border bg-surface p-6">
            <h2 className="text-base font-semibold text-text">Financial Results</h2>
            <p className="mt-0.5 text-xs text-text-muted">Sales and PAT are required; everything else is optional.</p>

            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <div>
                <label className="block text-xs font-medium text-text-muted">
                  Period end <span className="text-loss">*</span>
                </label>
                <input
                  type="date"
                  value={financialFields.periodEnd ?? ''}
                  onChange={(e) => financialField('periodEnd', e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border bg-bg px-2.5 py-1.5 text-sm text-text focus:border-accent focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-text-muted">Period type</label>
                <select
                  value={financialFields.periodType ?? 'QUARTERLY'}
                  onChange={(e) => financialField('periodType', e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border bg-bg px-2.5 py-1.5 text-sm text-text focus:border-accent focus:outline-none"
                >
                  <option value="QUARTERLY">Quarterly</option>
                  <option value="ANNUAL">Annual</option>
                </select>
              </div>
              <NumField label="Sales (₹ Cr)" field="sales" values={financialFields} onChange={financialField} required />
              <NumField label="PAT (₹ Cr)" field="pat" values={financialFields} onChange={financialField} required />
              <NumField label="EPS (₹)" field="eps" values={financialFields} onChange={financialField} />
              <NumField label="ROE %" field="roePercentage" values={financialFields} onChange={financialField} />
              <NumField label="ROCE %" field="rocePercentage" values={financialFields} onChange={financialField} />
              <NumField label="Operating margin %" field="operatingMarginPercentage" values={financialFields} onChange={financialField} />
              <NumField label="Net margin %" field="netMarginPercentage" values={financialFields} onChange={financialField} />
              <NumField label="Cash flow from ops (₹ Cr)" field="cashFlowFromOperations" values={financialFields} onChange={financialField} />
              <NumField label="Total assets (₹ Cr)" field="totalAssets" values={financialFields} onChange={financialField} />
              <NumField label="Current assets (₹ Cr)" field="currentAssets" values={financialFields} onChange={financialField} />
              <NumField label="Current liabilities (₹ Cr)" field="currentLiabilities" values={financialFields} onChange={financialField} />
              <NumField label="Total debt (₹ Cr)" field="totalDebt" values={financialFields} onChange={financialField} />
              <NumField label="Total equity (₹ Cr)" field="totalEquity" values={financialFields} onChange={financialField} />
              <NumField label="Interest expense (₹ Cr)" field="interestExpense" values={financialFields} onChange={financialField} />
              <NumField label="EBIT (₹ Cr)" field="ebit" values={financialFields} onChange={financialField} />
            </div>

            {financialMessage && <p className="mt-3 text-sm text-accent">{financialMessage}</p>}
            {financialMutation.error && <ErrorState message={(financialMutation.error as Error).message} onRetry={submitFinancial} />}

            <button
              onClick={submitFinancial}
              disabled={financialMutation.isPending || !financialFields.periodEnd || !financialFields.sales || !financialFields.pat}
              className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
            >
              {financialMutation.isPending ? 'Saving…' : 'Save Financial Results'}
            </button>
          </div>

          <div className="mt-6 rounded-2xl border border-border bg-surface p-6">
            <h2 className="text-base font-semibold text-text">Shareholding Pattern</h2>
            <p className="mt-0.5 text-xs text-text-muted">Promoter, FII, and DII % are required; MF and Public % are optional.</p>

            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <div>
                <label className="block text-xs font-medium text-text-muted">
                  Period end <span className="text-loss">*</span>
                </label>
                <input
                  type="date"
                  value={shareholdingFields.periodEnd ?? ''}
                  onChange={(e) => shareholdingField('periodEnd', e.target.value)}
                  className="mt-1 w-full rounded-lg border border-border bg-bg px-2.5 py-1.5 text-sm text-text focus:border-accent focus:outline-none"
                />
              </div>
              <NumField label="Promoter %" field="promoterPercentage" values={shareholdingFields} onChange={shareholdingField} required />
              <NumField label="FII %" field="fiiPercentage" values={shareholdingFields} onChange={shareholdingField} required />
              <NumField label="DII %" field="diiPercentage" values={shareholdingFields} onChange={shareholdingField} required />
              <NumField label="Mutual Fund % (of DII)" field="mfPercentage" values={shareholdingFields} onChange={shareholdingField} />
              <NumField label="Public %" field="publicPercentage" values={shareholdingFields} onChange={shareholdingField} />
            </div>

            {shareholdingMessage && <p className="mt-3 text-sm text-accent">{shareholdingMessage}</p>}
            {shareholdingMutation.error && <ErrorState message={(shareholdingMutation.error as Error).message} onRetry={submitShareholding} />}

            <button
              onClick={submitShareholding}
              disabled={
                shareholdingMutation.isPending ||
                !shareholdingFields.periodEnd ||
                !shareholdingFields.promoterPercentage ||
                !shareholdingFields.fiiPercentage ||
                !shareholdingFields.diiPercentage
              }
              className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
            >
              {shareholdingMutation.isPending ? 'Saving…' : 'Save Shareholding Pattern'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
