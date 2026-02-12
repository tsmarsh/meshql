import { useState } from 'react'

export default function AccountLookup({ onSearch, loading }) {
  const [accountNumber, setAccountNumber] = useState('')

  function handleSubmit(e) {
    e.preventDefault()
    if (accountNumber.trim()) {
      onSearch(accountNumber.trim())
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-md p-6">
      <label htmlFor="account" className="block text-sm font-medium text-gray-700 mb-2">
        Look up your account
      </label>
      <div className="flex gap-3">
        <input
          id="account"
          type="text"
          value={accountNumber}
          onChange={(e) => setAccountNumber(e.target.value)}
          placeholder="Enter account number (e.g. 100100-00001)"
          className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 text-lg font-mono"
          disabled={loading}
        />
        <button
          type="submit"
          disabled={loading || !accountNumber.trim()}
          className="px-6 py-3 bg-amber-600 text-white rounded-lg font-medium hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          Search
        </button>
      </div>
    </form>
  )
}
