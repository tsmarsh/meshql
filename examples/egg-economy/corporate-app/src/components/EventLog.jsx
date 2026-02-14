import { useState, useEffect } from 'react'
import { graphqlQuery, restPost } from '../api'
import CsvUploader from './CsvUploader'

const QUALITIES = ['grade_a', 'grade_b', 'cracked', 'double_yolk']

export default function EventLog({ farmId }) {
  const [reports, setReports] = useState([])
  const [hens, setHens] = useState([])
  const [coops, setCoops] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [showCsv, setShowCsv] = useState(false)
  const [sortField, setSortField] = useState('timestamp')
  const [sortDir, setSortDir] = useState(-1)

  // Form state
  const [henId, setHenId] = useState('')
  const [eggs, setEggs] = useState('1')
  const [quality, setQuality] = useState('grade_a')
  const [submitting, setSubmitting] = useState(false)

  function loadData() {
    setLoading(true)
    Promise.all([
      graphqlQuery('/lay_report/graph', `{ getByFarm(id: "${farmId}") { id hen_id coop_id farm_id eggs timestamp quality hen { id name } } }`, 'getByFarm'),
      graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name hens { id name } } }`, 'getByFarm'),
    ]).then(([reportData, coopData]) => {
      setReports(reportData || [])
      setCoops(coopData || [])
      const allHens = (coopData || []).flatMap(c => (c.hens || []).map(h => ({ ...h, coop_id: c.id, coop_name: c.name })))
      setHens(allHens)
      if (allHens.length > 0 && !henId) setHenId(allHens[0].id)
    }).catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadData() }, [farmId])

  function handleSort(field) {
    if (sortField === field) { setSortDir(-sortDir) }
    else { setSortField(field); setSortDir(field === 'timestamp' ? -1 : 1) }
  }

  const sorted = [...reports].sort((a, b) => {
    const av = a[sortField], bv = b[sortField]
    if (sortField === 'eggs') return ((av || 0) - (bv || 0)) * sortDir
    return String(av || '').localeCompare(String(bv || '')) * sortDir
  })

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    const hen = hens.find(h => h.id === henId)
    try {
      await restPost('/lay_report/api', {
        hen_id: henId,
        coop_id: hen?.coop_id || '',
        farm_id: farmId,
        eggs: parseInt(eggs),
        timestamp: new Date().toISOString(),
        quality
      })
      setShowForm(false)
      setEggs('1')
      loadData()
    } catch (e) { setError(e.message) }
    finally { setSubmitting(false) }
  }

  function mapLayReportRow(row) {
    const henName = row.hen_name || row.hen
    const hen = hens.find(h => h.name.toLowerCase() === (henName || '').toLowerCase())
    if (!hen) throw new Error(`Hen "${henName}" not found`)
    return {
      hen_id: hen.id,
      coop_id: hen.coop_id,
      farm_id: farmId,
      eggs: parseInt(row.eggs) || 1,
      timestamp: row.timestamp || new Date().toISOString(),
      quality: row.quality || 'grade_a'
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-slate-800">Lay Reports</h2>
        <div className="flex gap-3">
          <button onClick={() => { setShowForm(!showForm); setShowCsv(false) }}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
            {showForm ? 'Cancel' : 'Add Report'}
          </button>
          <button onClick={() => { setShowCsv(!showCsv); setShowForm(false) }}
            className="px-4 py-2 bg-slate-600 text-white rounded-lg text-sm font-medium hover:bg-slate-700 transition-colors">
            {showCsv ? 'Hide CSV' : 'CSV Upload'}
          </button>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">{error}</div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-md p-5 mb-6 grid grid-cols-4 gap-4 items-end">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Hen</label>
            <select value={henId} onChange={(e) => setHenId(e.target.value)}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500">
              {hens.map((h) => <option key={h.id} value={h.id}>{h.name} ({h.coop_name})</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Eggs</label>
            <input type="number" value={eggs} onChange={(e) => setEggs(e.target.value)} min="1" required
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Quality</label>
            <select value={quality} onChange={(e) => setQuality(e.target.value)}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500">
              {QUALITIES.map((q) => <option key={q} value={q}>{q.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
          <button type="submit" disabled={submitting || !henId}
            className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-50 transition-colors">
            {submitting ? 'Logging...' : 'Log'}
          </button>
        </form>
      )}

      {showCsv && (
        <div className="mb-6">
          <CsvUploader
            endpoint="/lay_report/api"
            mapRow={mapLayReportRow}
            label="Upload Lay Reports CSV (columns: hen_name, eggs, quality, timestamp)"
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
                <SortHeader field="timestamp" current={sortField} dir={sortDir} onSort={handleSort}>Time</SortHeader>
                <th className="px-6 py-3 font-medium">Hen</th>
                <SortHeader field="eggs" current={sortField} dir={sortDir} onSort={handleSort}>Eggs</SortHeader>
                <SortHeader field="quality" current={sortField} dir={sortDir} onSort={handleSort}>Quality</SortHeader>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sorted.map((r) => (
                <tr key={r.id} className="hover:bg-slate-50 transition-colors">
                  <td className="px-6 py-3 text-slate-600 text-xs">{new Date(r.timestamp).toLocaleString()}</td>
                  <td className="px-6 py-3 font-medium text-slate-900">{r.hen?.name || '—'}</td>
                  <td className="px-6 py-3 text-slate-900 font-medium">{r.eggs}</td>
                  <td className="px-6 py-3">
                    <span className="inline-block px-2.5 py-0.5 bg-green-50 text-green-700 rounded-full text-xs font-medium">
                      {(r.quality || '').replace(/_/g, ' ')}
                    </span>
                  </td>
                </tr>
              ))}
              {sorted.length === 0 && (
                <tr><td colSpan="4" className="px-6 py-8 text-center text-slate-400">No lay reports found.</td></tr>
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
