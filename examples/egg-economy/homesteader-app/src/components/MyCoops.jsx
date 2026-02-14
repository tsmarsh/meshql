import { useState, useEffect } from 'react'
import { graphqlQuery, restPost } from '../api'

const COOP_TYPES = ['barn', 'freerange', 'industrial', 'mobile', 'backyard']

export default function MyCoops({ farmId }) {
  const [coops, setCoops] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showAdd, setShowAdd] = useState(false)
  const [showAddHen, setShowAddHen] = useState(null)

  // Add coop form
  const [coopName, setCoopName] = useState('')
  const [coopCapacity, setCoopCapacity] = useState('30')
  const [coopType, setCoopType] = useState('freerange')
  const [submitting, setSubmitting] = useState(false)

  // Add hen form
  const [henName, setHenName] = useState('')
  const [henBreed, setHenBreed] = useState('Rhode Island Red')
  const [submittingHen, setSubmittingHen] = useState(false)

  function loadCoops() {
    setLoading(true)
    graphqlQuery('/coop/graph', `{ getByFarm(id: "${farmId}") { id name capacity coop_type hens { id name breed status } } }`, 'getByFarm')
      .then((data) => setCoops(data || []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadCoops() }, [farmId])

  async function handleAddCoop(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await restPost('/coop/api', {
        name: coopName,
        farm_id: farmId,
        capacity: parseInt(coopCapacity),
        coop_type: coopType
      })
      setCoopName('')
      setCoopCapacity('30')
      setShowAdd(false)
      loadCoops()
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleAddHen(e) {
    e.preventDefault()
    setSubmittingHen(true)
    setError(null)
    try {
      await restPost('/hen/api', {
        name: henName,
        coop_id: showAddHen,
        breed: henBreed,
        dob: new Date().toISOString().split('T')[0],
        status: 'active'
      })
      setHenName('')
      setHenBreed('Rhode Island Red')
      setShowAddHen(null)
      loadCoops()
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmittingHen(false)
    }
  }

  if (loading) {
    return (
      <div className="text-center py-12 text-amber-600">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent"></div>
        <p className="mt-2">Loading coops...</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-amber-900">My Coops</h2>
        <button
          onClick={() => setShowAdd(!showAdd)}
          className="px-4 py-2 bg-amber-700 text-white rounded-xl text-sm font-medium hover:bg-amber-800 transition-colors active:scale-[0.97]"
        >
          {showAdd ? 'Cancel' : 'Add Coop'}
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">{error}</div>
      )}

      {/* Add Coop Form */}
      {showAdd && (
        <form onSubmit={handleAddCoop} className="bg-white rounded-xl shadow-md p-4 space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Coop Name</label>
            <input
              type="text" value={coopName} onChange={(e) => setCoopName(e.target.value)}
              required placeholder="e.g. Backyard Run"
              className="w-full px-4 py-3 border border-gray-300 rounded-xl text-base focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Capacity</label>
              <input
                type="number" value={coopCapacity} onChange={(e) => setCoopCapacity(e.target.value)}
                min="1" required
                className="w-full px-4 py-3 border border-gray-300 rounded-xl text-base focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
              <select
                value={coopType} onChange={(e) => setCoopType(e.target.value)}
                className="w-full px-4 py-3 border border-gray-300 rounded-xl text-base focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
              >
                {COOP_TYPES.map((t) => (
                  <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                ))}
              </select>
            </div>
          </div>
          <button
            type="submit" disabled={submitting || !coopName}
            className="w-full py-3 bg-green-600 text-white rounded-xl font-medium hover:bg-green-700 disabled:opacity-50 transition-colors active:scale-[0.98]"
          >
            {submitting ? 'Adding...' : 'Add Coop'}
          </button>
        </form>
      )}

      {/* Coop List */}
      {coops.map((coop) => (
        <div key={coop.id} className="bg-white rounded-xl shadow-md p-4">
          <div className="flex items-center justify-between mb-2">
            <div>
              <h3 className="font-semibold text-amber-900">{coop.name}</h3>
              <div className="text-xs text-gray-500">
                {coop.coop_type?.replace(/_/g, ' ')} &middot; Capacity: {coop.capacity}
              </div>
            </div>
            <span className="text-sm font-medium text-gray-600">
              {(coop.hens || []).length} hens
            </span>
          </div>
          {(coop.hens && coop.hens.length > 0) && (
            <div className="flex flex-wrap gap-2 mt-2">
              {coop.hens.map((hen) => (
                <span key={hen.id} className="inline-block px-2.5 py-1 bg-amber-50 text-amber-800 rounded-lg text-xs font-medium">
                  {hen.name}
                </span>
              ))}
            </div>
          )}
          {showAddHen === coop.id ? (
            <form onSubmit={handleAddHen} className="mt-3 space-y-2 border-t pt-3">
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text" value={henName} onChange={(e) => setHenName(e.target.value)}
                  required placeholder="Hen name"
                  className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500"
                />
                <input
                  type="text" value={henBreed} onChange={(e) => setHenBreed(e.target.value)}
                  placeholder="Breed"
                  className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-amber-500"
                />
              </div>
              <div className="flex gap-2">
                <button type="submit" disabled={submittingHen || !henName}
                  className="flex-1 py-2 bg-green-600 text-white rounded-lg text-sm font-medium disabled:opacity-50 transition-colors"
                >
                  {submittingHen ? 'Adding...' : 'Add Hen'}
                </button>
                <button type="button" onClick={() => setShowAddHen(null)}
                  className="px-4 py-2 border border-gray-300 text-gray-600 rounded-lg text-sm font-medium transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <button
              onClick={() => setShowAddHen(coop.id)}
              className="mt-2 text-sm text-amber-700 font-medium hover:text-amber-900 transition-colors"
            >
              + Add Hen
            </button>
          )}
        </div>
      ))}
      {coops.length === 0 && (
        <p className="text-gray-500 text-center py-8">No coops yet. Add one above!</p>
      )}
    </div>
  )
}
