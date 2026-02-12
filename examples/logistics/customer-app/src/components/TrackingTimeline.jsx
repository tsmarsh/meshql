const STATUS_CONFIG = {
  label_created:       { color: 'bg-gray-400',   icon: '🏷️' },
  picked_up:           { color: 'bg-blue-400',   icon: '📬' },
  arrived_at_facility: { color: 'bg-blue-500',   icon: '🏭' },
  departed_facility:   { color: 'bg-blue-600',   icon: '🚚' },
  in_transit:          { color: 'bg-indigo-500', icon: '✈️' },
  out_for_delivery:    { color: 'bg-yellow-500', icon: '🚛' },
  delivered:           { color: 'bg-green-500',  icon: '✅' },
  delivery_attempted:  { color: 'bg-orange-500', icon: '🔔' },
  exception:           { color: 'bg-red-500',    icon: '⚠️' },
  returned_to_sender:  { color: 'bg-red-600',    icon: '↩️' },
}

function formatStatus(status) {
  return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatTimestamp(ts) {
  const d = new Date(ts)
  return d.toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true
  })
}

export default function TrackingTimeline({ updates }) {
  const sorted = [...updates].sort((a, b) =>
    new Date(b.timestamp) - new Date(a.timestamp)
  )

  return (
    <div className="bg-white rounded-xl shadow-md p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-6">Tracking History</h3>
      <div className="relative">
        {sorted.map((update, i) => {
          const config = STATUS_CONFIG[update.status] || STATUS_CONFIG.label_created
          const isLast = i === sorted.length - 1

          return (
            <div key={update.id || i} className="flex gap-4 pb-6 last:pb-0">
              {/* Timeline line + dot */}
              <div className="flex flex-col items-center">
                <div className={`w-10 h-10 rounded-full ${config.color} flex items-center justify-center text-lg shrink-0`}>
                  {config.icon}
                </div>
                {!isLast && <div className="w-0.5 bg-gray-200 flex-1 mt-1"></div>}
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0 pt-1">
                <div className="flex items-baseline justify-between gap-2">
                  <p className="font-medium text-gray-900">
                    {formatStatus(update.status)}
                  </p>
                  <time className="text-xs text-gray-400 whitespace-nowrap">
                    {formatTimestamp(update.timestamp)}
                  </time>
                </div>
                <p className="text-sm text-gray-600 mt-0.5">{update.location}</p>
                {update.notes && (
                  <p className="text-sm text-gray-500 mt-1">{update.notes}</p>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
