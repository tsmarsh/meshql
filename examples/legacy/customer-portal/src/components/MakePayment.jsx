import { useState } from 'react'

const API_BASE = import.meta.env.PROD ? '/api' : '/api'

const METHODS = [
  { value: 'web', label: 'Web Payment' },
  { value: 'electronic', label: 'Electronic Transfer' },
  { value: 'check', label: 'Check' },
  { value: 'cash', label: 'Cash' },
  { value: 'phone', label: 'Phone Payment' },
]

export default function MakePayment({ customerId, onPaymentMade, onCancel }) {
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('web')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    const today = new Date().toISOString().split('T')[0]

    try {
      const res = await fetch(`${API_BASE}/payment/api`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customer_id: customerId,
          payment_date: today,
          amount: parseFloat(amount),
          method: method,
          status: 'success'
        })
      })

      if (!res.ok) {
        throw new Error(`Payment failed (${res.status})`)
      }

      setSuccess(true)
      setTimeout(() => {
        onPaymentMade()
      }, 2000)
    } catch (e) {
      setError(e.message || 'Failed to process payment.')
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center">
        <div className="text-green-700 text-lg font-semibold">Payment submitted successfully</div>
        <p className="text-green-600 text-sm mt-1">Your account will be updated shortly.</p>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl shadow-md p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900">Make a Payment</h3>
        <button
          onClick={onCancel}
          className="text-gray-400 hover:text-gray-600 text-sm transition-colors"
        >
          Cancel
        </button>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-gray-700 mb-1">
            Amount ($)
          </label>
          <input
            id="amount"
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="0.00"
            required
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500 font-mono text-lg"
            disabled={submitting}
          />
        </div>

        <div>
          <label htmlFor="method" className="block text-sm font-medium text-gray-700 mb-1">
            Payment Method
          </label>
          <select
            id="method"
            value={method}
            onChange={(e) => setMethod(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
            disabled={submitting}
          >
            {METHODS.map((m) => (
              <option key={m.value} value={m.value}>{m.label}</option>
            ))}
          </select>
        </div>

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={submitting || !amount}
            className="flex-1 px-4 py-2.5 bg-amber-600 text-white rounded-lg font-medium hover:bg-amber-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? 'Processing...' : 'Submit Payment'}
          </button>
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg font-medium hover:bg-gray-50 disabled:opacity-50 transition-colors"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
