import { useState, useEffect } from 'react'
import { graphqlQuery } from '../api'

export default function Dashboard({ farmId, farmName }) {
  const [output, setOutput] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!farmId) return
    setLoading(true)
    graphqlQuery('/farm_output/graph', `{ getByFarm(id: "${farmId}") { id eggs_today eggs_week eggs_month active_hens total_hens avg_per_hen_per_week farm_type } }`, 'getByFarm')
      .then((data) => setOutput(data?.[0] || null))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [farmId])

  if (loading) {
    return (
      <div className="text-center py-16 text-slate-500">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
      </div>
    )
  }

  return (
    <div>
      <h2 className="text-2xl font-bold text-slate-800 mb-6">
        Dashboard {farmName && <span className="text-slate-400 font-normal text-lg">&mdash; {farmName}</span>}
      </h2>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">{error}</div>
      )}

      {!output && !error && (
        <div className="bg-white rounded-xl shadow-md p-8 text-center text-slate-500">
          No production data yet for this farm. Create coops, add hens, and log events to see stats.
        </div>
      )}

      {output && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard label="Eggs Today" value={output.eggs_today || 0} color="blue" />
          <KpiCard label="Eggs This Week" value={output.eggs_week || 0} color="green" />
          <KpiCard label="Eggs This Month" value={output.eggs_month || 0} color="purple" />
          <KpiCard label="Active Hens" value={output.active_hens || 0} color="amber" />
          <KpiCard label="Total Hens" value={output.total_hens || 0} color="slate" />
          <KpiCard label="Avg/Hen/Week" value={output.avg_per_hen_per_week?.toFixed(1) || '0.0'} color="teal" />
          <KpiCard label="Farm Type" value={(output.farm_type || '').replace(/_/g, ' ')} color="indigo" text />
        </div>
      )}
    </div>
  )
}

function KpiCard({ label, value, color, text }) {
  const colors = {
    blue: 'text-blue-700',
    green: 'text-green-700',
    purple: 'text-purple-700',
    amber: 'text-amber-700',
    slate: 'text-slate-700',
    teal: 'text-teal-700',
    indigo: 'text-indigo-700',
  }
  return (
    <div className="bg-white rounded-xl shadow-md p-5">
      <div className="text-xs text-slate-500 uppercase tracking-wide font-medium">{label}</div>
      <div className={`mt-2 font-bold ${colors[color] || 'text-slate-900'} ${text ? 'text-lg capitalize' : 'text-3xl'}`}>
        {typeof value === 'number' ? value.toLocaleString() : value}
      </div>
    </div>
  )
}
