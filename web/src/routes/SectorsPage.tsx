import { Fragment, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../lib/api'
import type { Sector } from '../types/instrument'
import type { TrackedInstrument } from '../types/financialData'
import { ErrorState } from '../components/ErrorState'
import { TableSkeleton } from '../components/TableSkeleton'

const NO_PARENT = ''
const UNASSIGNED = ''

export function SectorsPage() {
  const queryClient = useQueryClient()

  const [newName, setNewName] = useState('')
  const [newParentId, setNewParentId] = useState(NO_PARENT)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editParentId, setEditParentId] = useState(NO_PARENT)

  const [deletingId, setDeletingId] = useState<string | null>(null)

  const [pendingReassignment, setPendingReassignment] = useState<Record<string, string>>({})

  const sectorsQuery = useQuery({
    queryKey: ['admin-sectors'],
    queryFn: () => apiFetch<Sector[]>('/admin/sectors'),
  })

  const instrumentsQuery = useQuery({
    queryKey: ['admin-instruments'],
    queryFn: () => apiFetch<TrackedInstrument[]>('/admin/instruments'),
  })

  function refreshSectors() {
    queryClient.invalidateQueries({ queryKey: ['admin-sectors'] })
    queryClient.invalidateQueries({ queryKey: ['sectors'] }) // Add Instrument's dropdown reads the same data
  }

  function refreshInstruments() {
    queryClient.invalidateQueries({ queryKey: ['admin-instruments'] })
    refreshSectors() // instrumentCount per sector changed
  }

  const createMutation = useMutation({
    mutationFn: () =>
      apiFetch<string>('/admin/sectors', {
        method: 'POST',
        body: JSON.stringify({ name: newName.trim(), parentSectorId: newParentId || null }),
      }),
    onSuccess: () => {
      setNewName('')
      setNewParentId(NO_PARENT)
      refreshSectors()
    },
  })

  const updateMutation = useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/admin/sectors/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ name: editName.trim(), parentSectorId: editParentId || null }),
      }),
    onSuccess: () => {
      setEditingId(null)
      refreshSectors()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/admin/sectors/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDeletingId(null)
      refreshSectors()
    },
  })

  const reassignMutation = useMutation({
    mutationFn: ({ instrumentId, sectorId }: { instrumentId: string; sectorId: string }) =>
      apiFetch<void>(`/admin/instruments/${instrumentId}/sector`, {
        method: 'PUT',
        body: JSON.stringify({ sectorId: sectorId || null }),
      }),
    onSuccess: (_data, { instrumentId }) => {
      setPendingReassignment((prev) => {
        const next = { ...prev }
        delete next[instrumentId]
        return next
      })
      refreshInstruments()
    },
  })

  function startEditing(sector: Sector) {
    setDeletingId(null)
    setEditingId(sector.id)
    setEditName(sector.name)
    setEditParentId(sector.parentSectorId ?? NO_PARENT)
  }

  const sectors = sectorsQuery.data ?? []
  const instruments = instrumentsQuery.data ?? []
  const canCreate = newName.trim().length > 0 && !createMutation.isPending
  const canSaveEdit = editName.trim().length > 0 && !updateMutation.isPending

  return (
    <div>
      <h1 className="text-2xl font-bold text-text">Sectors</h1>
      <p className="mt-1 text-sm text-text-muted">
        The sector tree every tracked instrument is classified under - feeds the Sector Engine's breadth/participation scoring and
        the Add Instrument form's sector picker. A sector can't be deleted while instruments or sub-sectors still reference it.
      </p>

      <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
        <h2 className="text-sm font-semibold text-text">Add Sector</h2>
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <input
            className="min-w-[12rem] flex-1 rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
            type="text"
            placeholder="Sector name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <select
            className="rounded-lg border border-border bg-bg px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
            value={newParentId}
            onChange={(e) => setNewParentId(e.target.value)}
          >
            <option value={NO_PARENT}>No parent (top-level)</option>
            {sectors.map((sector) => (
              <option key={sector.id} value={sector.id}>
                {sector.name}
              </option>
            ))}
          </select>
          <button
            className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-50"
            disabled={!canCreate}
            onClick={() => createMutation.mutate()}
          >
            {createMutation.isPending ? 'Adding…' : 'Add Sector'}
          </button>
        </div>
        {createMutation.isError && <ErrorState message={(createMutation.error as Error).message} onRetry={() => createMutation.mutate()} />}
      </div>

      {sectorsQuery.isLoading && <TableSkeleton columns={4} />}
      {sectorsQuery.error && <ErrorState message="Couldn't load sectors." onRetry={sectorsQuery.refetch} />}

      {sectors.length === 0 && !sectorsQuery.isLoading && !sectorsQuery.error && (
        <p className="mt-8 text-sm text-text-muted">No sectors yet - add one above.</p>
      )}

      {sectors.length > 0 && (
        <div className="mt-6 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Parent</th>
                <th className="px-4 py-3 font-medium">Instruments</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {sectors.map((sector) => (
                <Fragment key={sector.id}>
                  <tr className="border-b border-border last:border-0 hover:bg-bg">
                    <td className="px-4 py-3 font-semibold text-text">{sector.name}</td>
                    <td className="px-4 py-3 text-text-muted">{sector.parentName ?? '—'}</td>
                    <td className="px-4 py-3 text-text">{sector.instrumentCount}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-3">
                        <button
                          className="text-xs font-semibold text-accent hover:underline"
                          onClick={() => (editingId === sector.id ? setEditingId(null) : startEditing(sector))}
                        >
                          Edit
                        </button>
                        <button
                          className="text-xs font-semibold text-loss hover:underline"
                          onClick={() => {
                            setEditingId(null)
                            setDeletingId(deletingId === sector.id ? null : sector.id)
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                  {editingId === sector.id && (
                    <tr className="border-b border-border bg-bg">
                      <td colSpan={4} className="px-4 py-3">
                        <div className="flex flex-wrap items-end gap-3">
                          <input
                            className="min-w-[12rem] flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
                            type="text"
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                          />
                          <select
                            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text focus:border-accent focus:outline-none"
                            value={editParentId}
                            onChange={(e) => setEditParentId(e.target.value)}
                          >
                            <option value={NO_PARENT}>No parent (top-level)</option>
                            {sectors
                              .filter((s) => s.id !== sector.id)
                              .map((s) => (
                                <option key={s.id} value={s.id}>
                                  {s.name}
                                </option>
                              ))}
                          </select>
                          <button
                            className="text-xs font-semibold text-text-muted hover:underline"
                            onClick={() => setEditingId(null)}
                          >
                            Cancel
                          </button>
                          <button
                            className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-hover disabled:opacity-50"
                            disabled={!canSaveEdit}
                            onClick={() => updateMutation.mutate(sector.id)}
                          >
                            Save
                          </button>
                        </div>
                        {updateMutation.isError && <p className="mt-2 text-sm text-loss">{(updateMutation.error as Error).message}</p>}
                      </td>
                    </tr>
                  )}
                  {deletingId === sector.id && (
                    <tr className="border-b border-border bg-bg">
                      <td colSpan={4} className="px-4 py-3">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <p className="text-sm text-text">Delete "{sector.name}"? This can't be undone.</p>
                          <div className="flex items-center gap-3">
                            <button
                              className="text-xs font-semibold text-text-muted hover:underline"
                              onClick={() => setDeletingId(null)}
                            >
                              Cancel
                            </button>
                            <button
                              className="rounded-lg bg-loss px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
                              disabled={deleteMutation.isPending}
                              onClick={() => deleteMutation.mutate(sector.id)}
                            >
                              Confirm Delete
                            </button>
                          </div>
                        </div>
                        {deleteMutation.isError && <p className="mt-2 text-sm text-loss">{(deleteMutation.error as Error).message}</p>}
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="mt-10 text-lg font-semibold text-text">Instrument Sector Assignments</h2>
      <p className="mt-1 text-sm text-text-muted">
        Move an instrument to a different sector - also how to clear a sector blocked from deletion above.
      </p>

      {instrumentsQuery.isLoading && <TableSkeleton columns={3} />}
      {instrumentsQuery.error && <ErrorState message="Couldn't load instruments." onRetry={instrumentsQuery.refetch} />}

      {instruments.length > 0 && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border bg-surface">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-text-muted">
                <th className="px-4 py-3 font-medium">Symbol</th>
                <th className="px-4 py-3 font-medium">Company</th>
                <th className="px-4 py-3 font-medium">Sector</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {instruments.map((instrument) => {
                const currentSectorId = instrument.sectorId ?? UNASSIGNED
                const selectedSectorId = pendingReassignment[instrument.id] ?? currentSectorId
                const isDirty = selectedSectorId !== currentSectorId
                const isSaving = reassignMutation.isPending && reassignMutation.variables?.instrumentId === instrument.id
                return (
                  <tr key={instrument.id} className="border-b border-border last:border-0 hover:bg-bg">
                    <td className="px-4 py-3 font-semibold text-text">{instrument.symbol}</td>
                    <td className="px-4 py-3 text-text-muted">{instrument.name}</td>
                    <td className="px-4 py-3">
                      <select
                        className="rounded-lg border border-border bg-bg px-2 py-1.5 text-sm text-text focus:border-accent focus:outline-none"
                        value={selectedSectorId}
                        onChange={(e) =>
                          setPendingReassignment((prev) => ({ ...prev, [instrument.id]: e.target.value }))
                        }
                      >
                        <option value={UNASSIGNED}>Unassigned</option>
                        {sectors.map((sector) => (
                          <option key={sector.id} value={sector.id}>
                            {sector.name}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        className="text-xs font-semibold text-accent hover:underline disabled:cursor-not-allowed disabled:opacity-40"
                        disabled={!isDirty || isSaving}
                        onClick={() => reassignMutation.mutate({ instrumentId: instrument.id, sectorId: selectedSectorId })}
                      >
                        {isSaving ? 'Saving…' : 'Save'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {reassignMutation.isError && <p className="mt-2 text-sm text-loss">{(reassignMutation.error as Error).message}</p>}
    </div>
  )
}
