const STATUS_STYLES = {
  open:       { bg: 'bg-blue-100',  text: 'text-blue-700',  label: 'Open' },
  paid:       { bg: 'bg-green-100', text: 'text-green-700', label: 'Paid' },
  delinquent: { bg: 'bg-red-100',   text: 'text-red-700',   label: 'Delinquent' },
  cancelled:  { bg: 'bg-gray-100',  text: 'text-gray-700',  label: 'Cancelled' },
}

function formatCurrency(amount) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
}

export default function BillList({ bills }) {
  const sorted = [...bills].sort((a, b) =>
    new Date(b.bill_date) - new Date(a.bill_date)
  )

  if (sorted.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-md p-6 text-center text-gray-500">
        No bills on file.
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl shadow-md overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h3 className="text-lg font-semibold text-gray-900">Billing History</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-left text-gray-500 text-xs uppercase tracking-wide">
              <th className="px-6 py-3 font-medium">Bill Date</th>
              <th className="px-6 py-3 font-medium">Period</th>
              <th className="px-6 py-3 font-medium text-right">Amount</th>
              <th className="px-6 py-3 font-medium text-right">kWh Used</th>
              <th className="px-6 py-3 font-medium">Status</th>
              <th className="px-6 py-3 font-medium">Late</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {sorted.map((bill) => {
              const status = STATUS_STYLES[bill.status] || STATUS_STYLES.open
              return (
                <tr key={bill.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-3 font-medium text-gray-900">{bill.bill_date}</td>
                  <td className="px-6 py-3 text-gray-600">
                    {bill.period_from} to {bill.period_to}
                  </td>
                  <td className="px-6 py-3 text-right font-medium text-gray-900">
                    {formatCurrency(bill.total_amount)}
                  </td>
                  <td className="px-6 py-3 text-right text-gray-600">
                    {bill.kwh_used != null ? bill.kwh_used.toLocaleString() : '--'}
                  </td>
                  <td className="px-6 py-3">
                    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${status.bg} ${status.text}`}>
                      {status.label}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-gray-600">
                    {bill.late ? (
                      <span className="text-red-600 font-medium">Yes</span>
                    ) : (
                      <span className="text-gray-400">--</span>
                    )}
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
