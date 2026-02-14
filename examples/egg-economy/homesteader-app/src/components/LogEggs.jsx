import { useState, useEffect } from 'react'
import { graphqlQuery, restPost } from '../api'

const QUALITIES = [
  { value: 'grade_a', label: 'Grade A' },
  { value: 'grade_b', label: 'Grade B' },
  { value: 'cracked', label: 'Cracked' },
  { value: 'double_yolk', label: 'Double Yolk' },
]

export default function LogEggs({ farmId }) {
  const [hens, setHens] = useState([])
  const [coops, setCoops] = useState([])
  const [selectedHen, setSelectedHen] = useState(null)
  const [eggs, setEggs] = useState(1)
  const [quality, setQuality] = useState('grade_a')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name hens { id name breed status } } }`, 'getByFarm'),
    ]).then(([coopData]) => {
      setCoops(coopData || [])
      const allHens = (coopData || []).flatMap(c => (c.hens || []).map(h => ({ ...h, coop_id: c.id, coop_name: c.name })))
      setHens(allHens)
    }).catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [farmId])

  async function handleSubmit(e) {
    e.preventDefault()
    if (!selectedHen) return
    setSubmitting(true)
    setError(null)

    const timestamp = new Date().toISOString()

    try {
      await restPost('/lay_report/api', {
        hen_id: selectedHen.id,
        coop_id: selectedHen.coop_id,
        farm_id: farmId,
        eggs: parseInt(eggs),
        timestamp,
        quality
      })
      setSuccess(true)
      setSelectedHen(null)
      setEggs(1)
      setQuality('grade_a')
      setTimeout(() => setSuccess(false), 3000)
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
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

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-amber-900">Log Eggs</h2>

      {success && (
        <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-xl text-center font-medium">
          Eggs logged successfully!
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">
          {error}
        </div>
      )}

      {/* Hen Selector — Card Grid */}
      <div>
        <label className="block text-sm font-medium text-amber-800 mb-2">Select Hen</label>
        <div className="grid grid-cols-2 gap-3 max-h-64 overflow-y-auto">
          {hens.map((hen) => (
            <button
              key={hen.id}
              type="button"
              onClick={() => setSelectedHen(hen)}
              className={`p-4 rounded-xl text-left transition-all active:scale-[0.97] ${
                selectedHen?.id === hen.id
                  ? 'bg-amber-600 text-white shadow-lg'
                  : 'bg-white shadow-md hover:shadow-lg'
              }`}
            >
              <div className="font-semibold text-base">{hen.name}</div>
              <div className={`text-xs mt-1 ${selectedHen?.id === hen.id ? 'text-amber-100' : 'text-gray-500'}`}>
                {hen.breed}
              </div>
              <div className={`text-xs ${selectedHen?.id === hen.id ? 'text-amber-200' : 'text-gray-400'}`}>
                {hen.coop_name}
              </div>
            </button>
          ))}
        </div>
        {hens.length === 0 && (
          <p className="text-gray-500 text-sm text-center py-4">No hens found. Add some in My Coops.</p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Egg Count */}
        <div>
          <label className="block text-sm font-medium text-amber-800 mb-2">Egg Count</label>
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={() => setEggs(Math.max(1, eggs - 1))}
              className="w-14 h-14 rounded-xl bg-amber-200 text-amber-900 text-2xl font-bold active:bg-amber-300 transition-colors"
            >
              -
            </button>
            <span className="text-4xl font-bold text-amber-900 w-16 text-center">{eggs}</span>
            <button
              type="button"
              onClick={() => setEggs(eggs + 1)}
              className="w-14 h-14 rounded-xl bg-amber-200 text-amber-900 text-2xl font-bold active:bg-amber-300 transition-colors"
            >
              +
            </button>
          </div>
        </div>

        {/* Quality */}
        <div>
          <label className="block text-sm font-medium text-amber-800 mb-2">Quality</label>
          <div className="grid grid-cols-2 gap-2">
            {QUALITIES.map((q) => (
              <button
                key={q.value}
                type="button"
                onClick={() => setQuality(q.value)}
                className={`py-3 px-4 rounded-xl text-sm font-medium transition-all active:scale-[0.97] ${
                  quality === q.value
                    ? 'bg-green-600 text-white shadow-md'
                    : 'bg-white text-gray-700 shadow-sm hover:shadow-md'
                }`}
              >
                {q.label}
              </button>
            ))}
          </div>
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={submitting || !selectedHen}
          className="w-full py-4 bg-amber-700 text-white rounded-xl text-lg font-bold hover:bg-amber-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors active:scale-[0.98]"
        >
          {submitting ? 'Logging...' : 'Log Eggs'}
        </button>
      </form>
    </div>
  )
}
