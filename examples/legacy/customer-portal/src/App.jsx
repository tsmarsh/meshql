import { useState } from 'react'
import AccountLookup from './components/AccountLookup'
import AccountSummary from './components/AccountSummary'
import BillList from './components/BillList'
import UsageHistory from './components/UsageHistory'
import PaymentHistory from './components/PaymentHistory'
import MakePayment from './components/MakePayment'

const API_BASE = import.meta.env.PROD ? '/api' : '/api'

async function fetchCustomer(accountNumber) {
  const query = `{ getByAccountNumber(account_number: "${accountNumber}") {
    id account_number first_name last_name address city state zip phone email
    rate_class service_type status connected_date budget_billing paperless
    bills { id bill_date due_date period_from period_to total_amount kwh_used status late }
    meterReadings { id reading_date reading_value previous_value kwh_used reading_type estimated }
    payments { id payment_date amount method confirmation_number status }
  } }`

  const res = await fetch(`${API_BASE}/customer/graph`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query })
  })
  const json = await res.json()
  if (json.errors) throw new Error(json.errors[0].message)
  return json.data.getByAccountNumber
}

const TABS = [
  { key: 'bills', label: 'Bills' },
  { key: 'readings', label: 'Usage' },
  { key: 'payments', label: 'Payments' },
]

export default function App() {
  const [accountNumber, setAccountNumber] = useState('')
  const [customer, setCustomer] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [activeTab, setActiveTab] = useState('bills')
  const [showPaymentForm, setShowPaymentForm] = useState(false)

  async function handleSearch(acctNum) {
    setAccountNumber(acctNum)
    setLoading(true)
    setError(null)
    setCustomer(null)
    setShowPaymentForm(false)
    try {
      const result = await fetchCustomer(acctNum)
      if (!result) {
        setError('No account found with that account number.')
      } else {
        setCustomer(result)
      }
    } catch (e) {
      setError(e.message || 'Failed to fetch account information.')
    } finally {
      setLoading(false)
    }
  }

  function handlePaymentMade() {
    setShowPaymentForm(false)
    // Refresh the customer data to show the new payment
    if (accountNumber) {
      handleSearch(accountNumber)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-amber-600 text-white shadow-lg">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <h1 className="text-3xl font-bold">Springfield Electric Cooperative</h1>
          <p className="text-amber-100 mt-1">Est. 1952 &mdash; Customer Portal</p>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8">
        <AccountLookup onSearch={handleSearch} loading={loading} />

        {error && (
          <div className="mt-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {loading && (
          <div className="mt-8 text-center text-gray-500">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-amber-500 border-t-transparent"></div>
            <p className="mt-2">Looking up your account...</p>
          </div>
        )}

        {customer && !loading && (
          <div className="mt-8 space-y-6">
            <AccountSummary customer={customer} />

            {/* Action bar */}
            <div className="flex items-center justify-between">
              {/* Tab bar */}
              <div className="flex border-b border-gray-200">
                {TABS.map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => { setActiveTab(tab.key); setShowPaymentForm(false) }}
                    className={`px-5 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      activeTab === tab.key
                        ? 'border-amber-600 text-amber-700'
                        : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>

              {/* Make a Payment button */}
              <button
                onClick={() => setShowPaymentForm(true)}
                className="px-4 py-2 bg-amber-600 text-white rounded-lg text-sm font-medium hover:bg-amber-700 transition-colors"
              >
                Make a Payment
              </button>
            </div>

            {/* Payment form overlay */}
            {showPaymentForm && (
              <MakePayment
                customerId={customer.id}
                onPaymentMade={handlePaymentMade}
                onCancel={() => setShowPaymentForm(false)}
              />
            )}

            {/* Tab content */}
            {activeTab === 'bills' && (
              <BillList bills={customer.bills || []} />
            )}
            {activeTab === 'readings' && (
              <UsageHistory readings={customer.meterReadings || []} />
            )}
            {activeTab === 'payments' && (
              <PaymentHistory payments={customer.payments || []} />
            )}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="mt-16 py-6 text-center text-gray-400 text-sm border-t">
        Powered by <a href="https://github.com/tsmarsh/meshql" className="text-amber-600 hover:underline" target="_blank" rel="noopener noreferrer">MeshQL</a>
      </footer>
    </div>
  )
}
