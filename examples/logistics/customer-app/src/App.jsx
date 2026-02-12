import { useState } from 'react'
import TrackingSearch from './components/TrackingSearch'
import PackageDetails from './components/PackageDetails'
import TrackingTimeline from './components/TrackingTimeline'
import ShipmentInfo from './components/ShipmentInfo'

const API_BASE = import.meta.env.PROD ? '/api' : '/api'

async function fetchPackage(trackingNumber) {
  const query = `{ getByTrackingNumber(tracking_number: "${trackingNumber}") {
    id description weight recipient recipient_address tracking_number
    shipment { id destination carrier status estimated_delivery }
    warehouse { id name city state }
    trackingUpdates { id status location timestamp notes }
  } }`

  const res = await fetch(`${API_BASE}/package/graph`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query })
  })
  const json = await res.json()
  if (json.errors) throw new Error(json.errors[0].message)
  return json.data.getByTrackingNumber
}

export default function App() {
  const [pkg, setPkg] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function handleSearch(trackingNumber) {
    setLoading(true)
    setError(null)
    setPkg(null)
    try {
      const result = await fetchPackage(trackingNumber)
      if (!result) {
        setError('No package found with that tracking number.')
      } else {
        setPkg(result)
      }
    } catch (e) {
      setError(e.message || 'Failed to fetch package information.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-indigo-700 text-white shadow-lg">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <h1 className="text-3xl font-bold">SwiftShip</h1>
          <p className="text-indigo-200 mt-1">Package Tracking</p>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8">
        <TrackingSearch onSearch={handleSearch} loading={loading} />

        {error && (
          <div className="mt-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {loading && (
          <div className="mt-8 text-center text-gray-500">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-indigo-500 border-t-transparent"></div>
            <p className="mt-2">Looking up your package...</p>
          </div>
        )}

        {pkg && !loading && (
          <div className="mt-8 space-y-6">
            <PackageDetails pkg={pkg} />
            {pkg.shipment && <ShipmentInfo shipment={pkg.shipment} warehouse={pkg.warehouse} />}
            {pkg.trackingUpdates && pkg.trackingUpdates.length > 0 && (
              <TrackingTimeline updates={pkg.trackingUpdates} />
            )}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="mt-16 py-6 text-center text-gray-400 text-sm border-t">
        Powered by <a href="https://github.com/tsmarsh/meshql" className="text-indigo-500 hover:underline" target="_blank" rel="noopener noreferrer">MeshQL</a>
      </footer>
    </div>
  )
}
