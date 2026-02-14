import { useState, useEffect } from 'react'
import { graphqlQuery } from '../api'

export default function WeeklySummary({ farmId }) {
  const [farmOutput, setFarmOutput] = useState(null)
  const [productivities, setProductivities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      graphqlQuery('/farm_output/graph', `{ getByFarm(id: "${farmId}") { id eggs_today eggs_week eggs_month active_hens total_hens avg_per_hen_per_week } }`, 'getByFarm'),
      graphqlQuery('/hen_productivity/graph', '{ getAll { id hen_id eggs_today eggs_week total_eggs quality_rate hen { id name breed } } }', 'getAll'),
    ]).then(([outputs, prods]) => {
      setFarmOutput(outputs?.[0] || null)
      setProductivities(prods || [])
    }).catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [farmId])

  if (loading) {
    return (
      <div className="text-center py-12 text-amber-600">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent"></div>
        <p className="mt-2">Loading summary...</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-amber-900">Weekly Summary</h2>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">{error}</div>
      )}

      {!farmOutput && !error && (
        <div className="text-center text-gray-500 py-8">
          <p>No production data yet.</p>
          <p className="text-sm mt-1">Log some eggs to see your summary!</p>
        </div>
      )}

      {farmOutput && (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-white rounded-xl shadow-md p-4 text-center">
              <div className="text-3xl font-bold text-amber-800">{farmOutput.eggs_today || 0}</div>
              <div className="text-xs text-gray-500 mt-1">Eggs Today</div>
            </div>
            <div className="bg-white rounded-xl shadow-md p-4 text-center">
              <div className="text-3xl font-bold text-green-700">{farmOutput.eggs_week || 0}</div>
              <div className="text-xs text-gray-500 mt-1">Eggs This Week</div>
            </div>
            <div className="bg-white rounded-xl shadow-md p-4 text-center">
              <div className="text-3xl font-bold text-blue-700">{farmOutput.eggs_month || 0}</div>
              <div className="text-xs text-gray-500 mt-1">Eggs This Month</div>
            </div>
            <div className="bg-white rounded-xl shadow-md p-4 text-center">
              <div className="text-3xl font-bold text-purple-700">{farmOutput.avg_per_hen_per_week?.toFixed(1) || '0.0'}</div>
              <div className="text-xs text-gray-500 mt-1">Avg/Hen/Week</div>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow-md p-4">
            <div className="flex justify-between text-sm text-gray-600">
              <span>Active Hens</span>
              <span className="font-medium text-gray-900">{farmOutput.active_hens || 0}</span>
            </div>
            <div className="flex justify-between text-sm text-gray-600 mt-2">
              <span>Total Hens</span>
              <span className="font-medium text-gray-900">{farmOutput.total_hens || 0}</span>
            </div>
          </div>
        </>
      )}

      {/* Hen Productivity */}
      {productivities.length > 0 && (
        <>
          <h3 className="text-lg font-semibold text-amber-800 mt-2">Hen Leaderboard</h3>
          <div className="space-y-2">
            {[...productivities]
              .sort((a, b) => (b.total_eggs || 0) - (a.total_eggs || 0))
              .slice(0, 10)
              .map((p, i) => (
                <div key={p.id} className="bg-white rounded-xl shadow-sm p-3 flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-amber-100 text-amber-800 flex items-center justify-center text-sm font-bold">
                    {i + 1}
                  </div>
                  <div className="flex-1">
                    <div className="font-medium text-gray-900">{p.hen?.name || 'Unknown'}</div>
                    <div className="text-xs text-gray-500">{p.hen?.breed}</div>
                  </div>
                  <div className="text-right">
                    <div className="font-bold text-amber-800">{p.total_eggs || 0}</div>
                    <div className="text-xs text-gray-400">total eggs</div>
                  </div>
                </div>
              ))}
          </div>
        </>
      )}
    </div>
  )
}
