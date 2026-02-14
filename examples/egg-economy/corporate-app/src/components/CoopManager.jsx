import { useState, useEffect } from 'react'
import { graphqlQuery, restPost } from '../api'

const COOP_TYPES = ['barn', 'freerange', 'industrial', 'mobile', 'backyard']

export default function CoopManager({ farmId }) {
  const [coops, setCoops] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [capacity, setCapacity] = useState('200')
  const [coopType, setCoopType] = useState('barn')
  const [submitting, setSubmitting] = useState(false)
  const [sortField, setSortField] = useState('name')
  const [sortDir, setSortDir] = useState(1)

  function loadCoops() {
    setLoading(true)
    graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name capacity coop_type hens { id } } }`, 'getByFarm')
      .then((data) => setCoops(data || []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadCoops() }, [farmId])

  function handleSort(field) {
    if (sortField === field) { setSortDir(-sortDir) }
    else { setSortField(field); setSortDir(1) }
  }

  const sorted = [...coops].sort((a, b) => {
    const av = a[sortField], bv = b[sortField]
    if (typeof av === 'number') return (av - bv) * sortDir
    return String(av || '').localeCompare(String(bv || '')) * sortDir
  })

  async function handleAdd(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await restPost('/coop/api', { name, farm_id: farmId, capacity: parseInt(capacity), coop_type: coopType })
      setName(''); setCapacity('200'); setShowForm(false)
      loadCoops()
    } catch (e) { setError(e.message) }
    finally { setSubmitting(false) }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-slate-800">Coops</h2>
        <button
          onClick={() => setShowForm(!showForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          {showForm ? 'Cancel' : 'Add Coop'}
        </button>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">{error}</div>
      )}

      {showForm && (
        <form onSubmit={handleAdd} className="bg-white rounded-xl shadow-md p-5 mb-6 grid grid-cols-4 gap-4 items-end">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Name</label>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} required
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Capacity</label>
            <input type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} min="1" required
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Type</label>
            <select value={coopType} onChange={(e) => setCoopType(e.target.value)}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500">
              {COOP_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
          <button type="submit" disabled={submitting || !name}
            className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50 transition-colors">
            {submitting ? 'Adding...' : 'Add'}
          </button>
        </form>
      )}

      {loading ? (
        <div className="text-center py-12 text-slate-500">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-md overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 text-left text-slate-500 text-xs uppercase tracking-wide">
                <SortHeader field="name" current={sortField} dir={sortDir} onSort={handleSort}>Name</SortHeader>
                <SortHeader field="coop_type" current={sortField} dir={sortDir} onSort={handleSort}>Type</SortHeader>
                <SortHeader field="capacity" current={sortField} dir={sortDir} onSort={handleSort}>Capacity</SortHeader>
                <th className="px-6 py-3 font-medium">Hens</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sorted.map((c) => (
                <tr key={c.id} className="hover:bg-slate-50 transition-colors">
                  <td className="px-6 py-3 font-medium text-slate-900">{c.name}</td>
                  <td className="px-6 py-3">
                    <span className="inline-block px-2.5 py-0.5 bg-blue-50 text-blue-700 rounded-full text-xs font-medium">
                      {(c.coop_type || '').replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-slate-600">{c.capacity}</td>
                  <td className="px-6 py-3 text-slate-600">{(c.hens || []).length}</td>
                </tr>
              ))}
              {sorted.length === 0 && (
                <tr><td colSpan="4" className="px-6 py-8 text-center text-slate-400">No coops found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
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
