import { useState, useEffect } from 'react'
import { graphqlQuery } from '../api'

export default function ContainerView() {
  const [containers, setContainers] = useState([])
  const [inventories, setInventories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [sortField, setSortField] = useState('name')
  const [sortDir, setSortDir] = useState(1)

  useEffect(() => {
    Promise.all([
      graphqlQuery('/container/graph', '{ getAll { id name container_type capacity zone } }', 'getAll'),
      graphqlQuery('/container_inventory/graph', '{ getAll { id container_id current_eggs total_deposits total_withdrawals total_transfers_in total_transfers_out total_consumed utilization_pct } }', 'getAll'),
    ]).then(([containerData, invData]) => {
      setContainers(containerData || [])
      setInventories(invData || [])
    }).catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  function handleSort(field) {
    if (sortField === field) { setSortDir(-sortDir) }
    else { setSortField(field); setSortDir(1) }
  }

  const enriched = containers.map(c => {
    const inv = inventories.find(i => i.container_id === c.id) || {}
    return { ...c, ...inv }
  })

  const sorted = [...enriched].sort((a, b) => {
    const av = a[sortField], bv = b[sortField]
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * sortDir
    return String(av || '').localeCompare(String(bv || '')) * sortDir
  })

  if (loading) {
    return (
      <div className="text-center py-16 text-slate-500">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    )
  }

  return (
    <div>
      <h2 className="text-2xl font-bold text-slate-800 mb-6">Containers & Inventory</h2>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">{error}</div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <SummaryCard label="Containers" value={containers.length} />
        <SummaryCard label="Total in Storage" value={inventories.reduce((s, i) => s + (i.current_eggs || 0), 0)} />
        <SummaryCard label="Total Deposited" value={inventories.reduce((s, i) => s + (i.total_deposits || 0), 0)} />
        <SummaryCard label="Total Consumed" value={inventories.reduce((s, i) => s + (i.total_consumed || 0), 0)} />
      </div>

      <div className="bg-white rounded-xl shadow-md overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-slate-50 text-left text-slate-500 text-xs uppercase tracking-wide">
              <SortHeader field="name" current={sortField} dir={sortDir} onSort={handleSort}>Name</SortHeader>
              <SortHeader field="container_type" current={sortField} dir={sortDir} onSort={handleSort}>Type</SortHeader>
              <SortHeader field="zone" current={sortField} dir={sortDir} onSort={handleSort}>Zone</SortHeader>
              <th className="px-6 py-3 font-medium text-right">Capacity</th>
              <th className="px-6 py-3 font-medium text-right">Current</th>
              <th className="px-6 py-3 font-medium text-right">Utilization</th>
              <th className="px-6 py-3 font-medium text-right">Deposits</th>
              <th className="px-6 py-3 font-medium text-right">Consumed</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {sorted.map((c) => (
              <tr key={c.id} className="hover:bg-slate-50 transition-colors">
                <td className="px-6 py-3 font-medium text-slate-900">{c.name}</td>
                <td className="px-6 py-3">
                  <span className="inline-block px-2.5 py-0.5 bg-blue-50 text-blue-700 rounded-full text-xs font-medium">
                    {(c.container_type || '').replace(/_/g, ' ')}
                  </span>
                </td>
                <td className="px-6 py-3 text-slate-600">{c.zone}</td>
                <td className="px-6 py-3 text-right text-slate-600">{(c.capacity || 0).toLocaleString()}</td>
                <td className="px-6 py-3 text-right font-medium text-slate-900">{(c.current_eggs || 0).toLocaleString()}</td>
                <td className="px-6 py-3 text-right">
                  <UtilizationBadge pct={c.utilization_pct} />
                </td>
                <td className="px-6 py-3 text-right text-slate-600">{(c.total_deposits || 0).toLocaleString()}</td>
                <td className="px-6 py-3 text-right text-slate-600">{(c.total_consumed || 0).toLocaleString()}</td>
              </tr>
            ))}
            {sorted.length === 0 && (
              <tr><td colSpan="8" className="px-6 py-8 text-center text-slate-400">No containers found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function SummaryCard({ label, value }) {
  return (
    <div className="bg-white rounded-xl shadow-md p-4">
      <div className="text-xs text-slate-500 uppercase tracking-wide font-medium">{label}</div>
      <div className="mt-1 text-2xl font-bold text-slate-800">{typeof value === 'number' ? value.toLocaleString() : value}</div>
    </div>
  )
}

function UtilizationBadge({ pct }) {
  if (pct == null) return <span className="text-slate-400">—</span>
  const p = Math.round(pct)
  let color = 'bg-green-100 text-green-700'
  if (p > 80) color = 'bg-red-100 text-red-700'
  else if (p > 50) color = 'bg-yellow-100 text-yellow-700'
  return (
    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${color}`}>
      {p}%
    </span>
  )
}

function SortHeader({ field, current, dir, onSort, children }) {
  const active = current === field
  return (
    <th className="px-6 py-3 font-medium cursor-pointer hover:text-slate-700 select-none" onClick={() => onSort(field)}>
      {children} {active && (dir > 0 ? '\u25B2' : '\u25BC')}
    </th>
  )
}
