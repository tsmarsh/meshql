import { useState, useEffect } from 'react'
import { graphqlQuery } from '../api'

export default function FarmSetup({ onSelect }) {
  const [farms, setFarms] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    graphqlQuery('/farm/graph', '{ getAll { id name farm_type zone owner } }', 'getAll')
      .then((data) => setFarms(data || []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="min-h-screen bg-amber-50 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-amber-800">Homesteader</h1>
          <p className="text-amber-600 mt-2">Select your farm to get started</p>
        </div>

        {error && (
          <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
            {error}
          </div>
        )}

        {loading && (
          <div className="text-center text-amber-600">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent"></div>
            <p className="mt-2">Loading farms...</p>
          </div>
        )}

        {!loading && farms.length === 0 && !error && (
          <div className="text-center text-gray-500">
            <p>No farms found. Create one using the API first.</p>
          </div>
        )}

        {!loading && farms.length > 0 && (
          <div className="space-y-3">
            {farms.map((farm) => (
              <button
                key={farm.id}
                onClick={() => onSelect(farm.id, farm.name)}
                className="w-full bg-white rounded-xl shadow-md p-5 text-left hover:shadow-lg hover:bg-amber-50 transition-all active:scale-[0.98]"
              >
                <div className="font-semibold text-lg text-amber-900">{farm.name}</div>
                <div className="flex gap-3 mt-1 text-sm text-gray-500">
                  <span className="inline-block px-2 py-0.5 bg-amber-100 text-amber-700 rounded-full text-xs font-medium">
                    {(farm.farm_type || '').replace(/_/g, ' ')}
                  </span>
                  <span>{farm.zone}</span>
                </div>
                {farm.owner && <div className="text-xs text-gray-400 mt-1">{farm.owner}</div>}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
