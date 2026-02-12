import { useState } from 'react'

export default function TrackingSearch({ onSearch, loading }) {
  const [trackingNumber, setTrackingNumber] = useState('PKG-DEN1A001')

  function handleSubmit(e) {
    e.preventDefault()
    if (trackingNumber.trim()) {
      onSearch(trackingNumber.trim())
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-md p-6">
      <label htmlFor="tracking" className="block text-sm font-medium text-gray-700 mb-2">
        Enter your tracking number
      </label>
      <div className="flex gap-3">
        <input
          id="tracking"
          type="text"
          value={trackingNumber}
          onChange={(e) => setTrackingNumber(e.target.value)}
          placeholder="PKG-XXXXXXXX"
          className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-lg font-mono"
          disabled={loading}
        />
        <button
          type="submit"
          disabled={loading || !trackingNumber.trim()}
          className="px-6 py-3 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          Track
        </button>
      </div>
      <p className="mt-2 text-xs text-gray-400">
        Try: PKG-DEN1A001, PKG-DEN2C003, PKG-CHI3E005, PKG-ATL6K011
      </p>
    </form>
  )
}
