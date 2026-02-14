import { useState, useEffect } from 'react'
import { graphqlQuery } from '../api'

const STATUS_STYLES = {
  active:   { bg: 'bg-green-100', text: 'text-green-700', label: 'Active' },
  broody:   { bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Broody' },
  molting:  { bg: 'bg-orange-100', text: 'text-orange-700', label: 'Molting' },
  retired:  { bg: 'bg-gray-100', text: 'text-gray-700', label: 'Retired' },
}

export default function MyHens({ farmId }) {
  const [coops, setCoops] = useState([])
  const [selectedHen, setSelectedHen] = useState(null)
  const [layReports, setLayReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingReports, setLoadingReports] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name hens { id name breed status dob } } }`, 'getByFarm')
      .then((data) => setCoops(data || []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [farmId])

  async function viewHen(hen) {
    setSelectedHen(hen)
    setLoadingReports(true)
    try {
      const reports = await graphqlQuery(
        '/lay_report/graph',
        `{ getByHen(id: "${hen.id}") { id eggs timestamp quality } }`,
        'getByHen'
      )
      setLayReports(reports || [])
    } catch (e) {
      setLayReports([])
    } finally {
      setLoadingReports(false)
    }
  }

  if (loading) {
    return (
      <div className="text-center py-12 text-amber-600">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent"></div>
        <p className="mt-2">Loading hens...</p>
      </div>
    )
  }

  if (selectedHen) {
    return (
      <div className="space-y-4">
        <button
          onClick={() => { setSelectedHen(null); setLayReports([]) }}
          className="text-amber-700 text-sm font-medium"
        >
          &larr; Back to hens
        </button>
        <div className="bg-white rounded-xl shadow-md p-5">
          <h2 className="text-xl font-bold text-amber-900">{selectedHen.name}</h2>
          <div className="mt-2 space-y-1 text-sm text-gray-600">
            <p>Breed: <span className="font-medium text-gray-900">{selectedHen.breed}</span></p>
            {selectedHen.dob && <p>Born: <span className="font-medium text-gray-900">{selectedHen.dob}</span></p>}
            <p>Status: {(() => {
              const s = STATUS_STYLES[selectedHen.status] || STATUS_STYLES.active
              return <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}>{s.label}</span>
            })()}</p>
          </div>
        </div>

        <h3 className="text-lg font-semibold text-amber-800">Lay Reports</h3>
        {loadingReports ? (
          <div className="text-center text-amber-600 py-4">Loading...</div>
        ) : layReports.length === 0 ? (
          <div className="text-center text-gray-500 py-4">No lay reports yet.</div>
        ) : (
          <div className="space-y-2">
            {[...layReports].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).map((r) => (
              <div key={r.id} className="bg-white rounded-xl shadow-sm p-4 flex items-center justify-between">
                <div>
                  <div className="text-sm text-gray-500">{new Date(r.timestamp).toLocaleDateString()}</div>
                  <div className="text-xs text-gray-400">{(r.quality || '').replace(/_/g, ' ')}</div>
                </div>
                <div className="text-2xl font-bold text-amber-800">{r.eggs}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-amber-900">My Hens</h2>
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">{error}</div>
      )}
      {coops.map((coop) => (
        <div key={coop.id}>
          <h3 className="text-sm font-semibold text-amber-700 mb-2">{coop.name}</h3>
          {(!coop.hens || coop.hens.length === 0) ? (
            <p className="text-gray-400 text-sm ml-2">No hens in this coop.</p>
          ) : (
            <div className="space-y-2">
              {coop.hens.map((hen) => {
                const s = STATUS_STYLES[hen.status] || STATUS_STYLES.active
                return (
                  <button
                    key={hen.id}
                    onClick={() => viewHen(hen)}
                    className="w-full bg-white rounded-xl shadow-sm p-4 flex items-center justify-between hover:shadow-md transition-shadow active:scale-[0.98]"
                  >
                    <div className="text-left">
                      <div className="font-medium text-gray-900">{hen.name}</div>
                      <div className="text-xs text-gray-500">{hen.breed}</div>
                    </div>
                    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}>
                      {s.label}
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </div>
      ))}
      {coops.length === 0 && (
        <p className="text-gray-500 text-center py-8">No coops found for this farm.</p>
      )}
    </div>
  )
}
