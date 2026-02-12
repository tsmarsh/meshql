const TYPE_STYLES = {
  actual:   { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Actual' },
  estimated:{ bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Estimated' },
  customer: { bg: 'bg-blue-100',   text: 'text-blue-700',   label: 'Customer' },
}

export default function UsageHistory({ readings }) {
  const sorted = [...readings].sort((a, b) =>
    new Date(b.reading_date) - new Date(a.reading_date)
  )

  if (sorted.length === 0) {
    return (
      <div className="bg-white rounded-xl shadow-md p-6 text-center text-gray-500">
        No meter readings on file.
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl shadow-md overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h3 className="text-lg font-semibold text-gray-900">Meter Reading History</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-left text-gray-500 text-xs uppercase tracking-wide">
              <th className="px-6 py-3 font-medium">Date</th>
              <th className="px-6 py-3 font-medium text-right">Reading (kWh)</th>
              <th className="px-6 py-3 font-medium text-right">Previous</th>
              <th className="px-6 py-3 font-medium text-right">Usage (kWh)</th>
              <th className="px-6 py-3 font-medium">Type</th>
              <th className="px-6 py-3 font-medium">Estimated</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {sorted.map((reading) => {
              const typeStyle = TYPE_STYLES[reading.reading_type] || TYPE_STYLES.actual
              return (
                <tr key={reading.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-3 font-medium text-gray-900">{reading.reading_date}</td>
                  <td className="px-6 py-3 text-right text-gray-900 font-mono">
                    {reading.reading_value != null ? reading.reading_value.toLocaleString() : '--'}
                  </td>
                  <td className="px-6 py-3 text-right text-gray-600 font-mono">
                    {reading.previous_value != null ? reading.previous_value.toLocaleString() : '--'}
                  </td>
                  <td className="px-6 py-3 text-right font-medium text-gray-900">
                    {reading.kwh_used != null ? reading.kwh_used.toLocaleString() : '--'}
                  </td>
                  <td className="px-6 py-3">
                    <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${typeStyle.bg} ${typeStyle.text}`}>
                      {typeStyle.label}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-center">
                    {reading.estimated ? (
                      <span className="text-yellow-600 font-medium">Yes</span>
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
