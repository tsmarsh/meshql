import { useState, useEffect } from 'react'
import { graphqlQuery, restPost } from '../api'
import CsvUploader from './CsvUploader'

const STATUS_STYLES = {
  active:  { bg: 'bg-green-100', text: 'text-green-700' },
  broody:  { bg: 'bg-yellow-100', text: 'text-yellow-700' },
  molting: { bg: 'bg-orange-100', text: 'text-orange-700' },
  retired: { bg: 'bg-gray-100', text: 'text-gray-700' },
}

export default function HenManager({ farmId }) {
  const [coops, setCoops] = useState([])
  const [selectedCoopId, setSelectedCoopId] = useState('all')
  const [hens, setHens] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [sortField, setSortField] = useState('name')
  const [sortDir, setSortDir] = useState(1)
  const [showCsv, setShowCsv] = useState(false)

  function loadData() {
    setLoading(true)
    graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name hens { id name breed status dob } } }`, 'getByFarm')
      .then((data) => {
        const c = data || []
        setCoops(c)
        const allHens = c.flatMap(coop => (coop.hens || []).map(h => ({ ...h, coop_id: coop.id, coop_name: coop.name })))
        setHens(allHens)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadData() }, [farmId])

  function handleSort(field) {
    if (sortField === field) { setSortDir(-sortDir) }
    else { setSortField(field); setSortDir(1) }
  }

  const filtered = selectedCoopId === 'all' ? hens : hens.filter(h => h.coop_id === selectedCoopId)
  const sorted = [...filtered].sort((a, b) => {
    const av = a[sortField], bv = b[sortField]
    return String(av || '').localeCompare(String(bv || '')) * sortDir
  })

  function mapHenRow(row) {
    const coopName = row.coop_name || row.coop
    const coop = coops.find(c => c.name.toLowerCase() === (coopName || '').toLowerCase())
    if (!coop) throw new Error(`Coop "${coopName}" not found`)
    return {
      name: row.name,
      coop_id: coop.id,
      breed: row.breed || 'Unknown',
      dob: row.dob || new Date().toISOString().split('T')[0],
      status: row.status || 'active'
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-slate-800">Hens</h2>
        <div className="flex gap-3">
          <select
            value={selectedCoopId}
            onChange={(e) => setSelectedCoopId(e.target.value)}
            className="px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">All Coops</option>
            {coops.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <button
            onClick={() => setShowCsv(!showCsv)}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
          >
            {showCsv ? 'Hide CSV' : 'CSV Upload'}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">{error}</div>
      )}

      {showCsv && (
        <div className="mb-6">
          <CsvUploader
            endpoint="/hen/api"
            mapRow={mapHenRow}
            label="Upload Hens CSV (columns: name, coop_name, breed, dob, status)"
          />
          <button onClick={() => { setShowCsv(false); loadData() }} className="mt-2 text-sm text-blue-600 hover:underline">
            Refresh after upload
          </button>
        </div>
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
                <SortHeader field="breed" current={sortField} dir={sortDir} onSort={handleSort}>Breed</SortHeader>
                <SortHeader field="status" current={sortField} dir={sortDir} onSort={handleSort}>Status</SortHeader>
                <th className="px-6 py-3 font-medium">Coop</th>
                <th className="px-6 py-3 font-medium">DOB</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sorted.map((h) => {
                const s = STATUS_STYLES[h.status] || STATUS_STYLES.active
                return (
                  <tr key={h.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-6 py-3 font-medium text-slate-900">{h.name}</td>
                    <td className="px-6 py-3 text-slate-600">{h.breed}</td>
                    <td className="px-6 py-3">
                      <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}>
                        {(h.status || 'active').replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-slate-600">{h.coop_name}</td>
                    <td className="px-6 py-3 text-slate-500 text-xs">{h.dob || '—'}</td>
                  </tr>
                )
              })}
              {sorted.length === 0 && (
                <tr><td colSpan="5" className="px-6 py-8 text-center text-slate-400">No hens found.</td></tr>
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
