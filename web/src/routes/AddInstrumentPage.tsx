import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { Instrument, Sector, SecurityMasterEntry } from '../types/instrument'
import { ErrorState } from '../components/ErrorState'

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

export function AddInstrumentPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const prefillSymbol = searchParams.get('symbol')
  const [searchText, setSearchText] = useState(prefillSymbol ?? '')
  const [selected, setSelected] = useState<SecurityMasterEntry | null>(null)
  const [sectorName, setSectorName] = useState('')
  const [lastAdded, setLastAdded] = useState<Instrument | null>(null)
  const debouncedSearch = useDebounced(searchText, 300)

  const searchQuery = useQuery({
    queryKey: ['security-master-search', debouncedSearch],
    queryFn: () => apiFetch<SecurityMasterEntry[]>(`/admin/security-master/search?query=${encodeURIComponent(debouncedSearch)}`),
    enabled: debouncedSearch.trim().length >= 2 && !selected,
  })

  // Arriving from the Discovery page's "Promote" link with ?symbol=XYZ pre-fills the search box
  // (above) and, once the security-master search resolves, auto-selects the exact match - skips
  // the manual type-then-click step for a symbol the admin already decided to add.
  useEffect(() => {
    if (!prefillSymbol || selected || !searchQuery.data) {
      return
    }
    const exactMatch = searchQuery.data.find((entry) => entry.symbol === prefillSymbol)
    if (exactMatch) {
      setSelected(exactMatch)
    }
  }, [prefillSymbol, selected, searchQuery.data])

  const sectorsQuery = useQuery({
    queryKey: ['sectors'],
    queryFn: () => apiFetch<Sector[]>('/admin/sectors'),
  })

  const addMutation = useMutation({
    mutationFn: () =>
      apiFetch<Instrument>('/admin/instruments', {
        method: 'POST',
        body: JSON.stringify({ symbol: selected!.symbol, sectorName: sectorName.trim() }),
      }),
    onSuccess: (created) => {
      setLastAdded(created)
      setSelected(null)
      setSearchText('')
      setSectorName('')
      queryClient.invalidateQueries({ queryKey: ['sectors'] })
    },
  })

  function pickResult(entry: SecurityMasterEntry) {
    setSelected(entry)
    setSearchText(entry.symbol)
  }

  function clearSelection() {
    setSelected(null)
    setSearchText('')
  }

  const canSubmit = selected !== null && sectorName.trim().length > 0 && !addMutation.isPending

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold text-text">Add Instrument</h1>
      <p className="mt-1 text-sm text-text-muted">
        Search for a real NSE-listed company and pick it from the results - the symbol, company name, and ISIN come directly from
        NSE's own listings, nothing here is typed by hand.
      </p>

      {lastAdded && (
        <div className="mt-6 rounded-2xl border border-accent-soft bg-accent-soft p-4 text-sm text-text">
          <span className="font-semibold">{lastAdded.symbol}</span> ({lastAdded.companyName}) was added and is now tracked.
          Historical price data is loading in the background - it can take a minute or two before charts and scores are ready.
        </div>
      )}

      <div className="mt-6 space-y-5 rounded-2xl border border-border bg-surface p-6">
        <div>
          <label className="block text-sm font-medium text-text">Company or symbol</label>
          <input
            type="text"
            value={searchText}
            onChange={(e) => {
              setSearchText(e.target.value)
              setSelected(null)
            }}
            placeholder="Start typing a symbol or company name…"
            className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
          />

          {selected && (
            <div className="mt-2 flex items-center justify-between rounded-lg bg-accent-soft px-3 py-2 text-sm">
              <div>
                <span className="font-semibold text-text">{selected.symbol}</span>
                <span className="ml-2 text-text-muted">{selected.companyName}</span>
                <div className="text-xs text-text-muted">ISIN: {selected.isin}</div>
              </div>
              <button onClick={clearSelection} className="text-xs font-medium text-accent hover:underline">
                Change
              </button>
            </div>
          )}

          {!selected && debouncedSearch.trim().length >= 2 && (
            <div className="mt-2 max-h-72 overflow-y-auto rounded-lg border border-border">
              {searchQuery.isLoading && <p className="p-3 text-sm text-text-muted">Searching…</p>}
              {searchQuery.error && <p className="p-3 text-sm text-loss">Couldn't search - try again.</p>}
              {searchQuery.data && searchQuery.data.length === 0 && (
                <p className="p-3 text-sm text-text-muted">No NSE-listed match for "{debouncedSearch}".</p>
              )}
              {searchQuery.data?.map((entry) => (
                <button
                  key={entry.id}
                  onClick={() => pickResult(entry)}
                  className="block w-full border-b border-border px-3 py-2 text-left text-sm last:border-b-0 hover:bg-bg"
                >
                  <span className="font-semibold text-text">{entry.symbol}</span>
                  <span className="ml-2 text-text-muted">{entry.companyName}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-text">Sector</label>
          <input
            list="sector-options"
            type="text"
            value={sectorName}
            onChange={(e) => setSectorName(e.target.value)}
            placeholder="Pick an existing sector or type a new one"
            className="mt-1.5 w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
          />
          <datalist id="sector-options">
            {sectorsQuery.data?.map((sector) => <option key={sector.id} value={sector.name} />)}
          </datalist>
        </div>

        {addMutation.error && <ErrorState message={(addMutation.error as Error).message} onRetry={() => addMutation.mutate()} />}

        <button
          onClick={() => addMutation.mutate()}
          disabled={!canSubmit}
          className="w-full rounded-lg bg-accent px-4 py-2.5 text-sm font-semibold text-white hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
        >
          {addMutation.isPending ? 'Adding…' : 'Add Instrument'}
        </button>
      </div>
    </div>
  )
}
