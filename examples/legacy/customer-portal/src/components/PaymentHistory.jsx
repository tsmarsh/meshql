const METHOD_STYLES = {
  web:        { bg: 'bg-indigo-100', text: 'text-indigo-700', label: 'Web' },
  electronic: { bg: 'bg-blue-100',   text: 'text-blue-700',   label: 'Electronic' },
  check:      { bg: 'bg-gray-100',   text: 'text-gray-700',   label: 'Check' },
  cash:       { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Cash' },
  phone:      { bg: 'bg-purple-100', text: 'text-purple-700', label: 'Phone' },
}

const STATUS_STYLES = {
  success:  { bg: 'bg-green-100', text: 'text-green-700', label: 'Success' },
  pending:  { bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Pending' },
  failed:   { bg: 'bg-red-100',   text: 'text-red-700',   label: 'Failed' },
  reversed: { bg: 'bg-orange-100', text: 'text-orange-700', label: 'Reversed' },
}

function formatCurrency(amount) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
}

export default function PaymentHistory({ payments }) {
  const sorted = [...payments].sort((a, b) =>
    new Date(b.payment_date) - new Date(a.payment_date)
  )

  if (sorted.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-md p-6 text-center text-gray-500">
        No payments on file.
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl shadow-md overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h3 className="text-lg font-semibold text-gray-900">Payment History</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-left text-gray-500 text-xs uppercase tracking-wide">
              <th className="px-6 py-3 font-medium">Date</th>
              <th className="px-6 py-3 font-medium text-right">Amount</th>
              <th className="px-6 py-3 font-medium">Method</th>
              <th className="px-6 py-3 font-medium">Confirmation</th>
              <th className="px-6 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {sorted.map((payment) => {
              const method = METHOD_STYLES[payment.method] || METHOD_STYLES.check
              const status = STATUS_STYLES[payment.status] || STATUS_STYLES.pending
              return (
                <tr key={payment.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-3 font-medium text-gray-900">{payment.payment_date}</td>
                  <td className="px-6 py-3 text-right font-medium text-gray-900">
                    {formatCurrency(payment.amount)}
                  </td>
                  <td className="px-6 py-3">
                    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${method.bg} ${method.text}`}>
                      {method.label}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-gray-600 font-mono text-xs">
                    {payment.confirmation_number || '--'}
                  </td>
                  <td className="px-6 py-3">
                    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${status.bg} ${status.text}`}>
                      {status.label}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
