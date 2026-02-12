const STATUS_STYLES = {
  preparing:        { bg: 'bg-gray-100',   text: 'text-gray-700',   label: 'Preparing' },
  in_transit:       { bg: 'bg-blue-100',   text: 'text-blue-700',   label: 'In Transit' },
  out_for_delivery: { bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Out for Delivery' },
  delivered:        { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Delivered' },
  delayed:          { bg: 'bg-red-100',    text: 'text-red-700',    label: 'Delayed' },
  cancelled:        { bg: 'bg-red-100',    text: 'text-red-700',    label: 'Cancelled' },
}

export default function ShipmentInfo({ shipment, warehouse }) {
  const status = STATUS_STYLES[shipment.status] || STATUS_STYLES.preparing

  return (
    <div className="bg-white rounded-xl shadow-md p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Shipment Details</h3>
      <div className="grid grid-cols-2 gap-4 text-sm">
        <div>
          <span className="text-gray-500">Carrier</span>
          <p className="font-medium text-gray-900">{shipment.carrier}</p>
        </div>
        <div>
          <span className="text-gray-500">Status</span>
          <p>
            <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${status.bg} ${status.text}`}>
              {status.label}
            </span>
          </p>
        </div>
        <div>
          <span className="text-gray-500">Destination</span>
          <p className="font-medium text-gray-900">{shipment.destination}</p>
        </div>
        <div>
          <span className="text-gray-500">Estimated Delivery</span>
          <p className="font-medium text-gray-900">
            {shipment.estimated_delivery || 'Pending'}
          </p>
        </div>
        {warehouse && (
          <div className="col-span-2">
            <span className="text-gray-500">Origin Warehouse</span>
            <p className="font-medium text-gray-900">
              {warehouse.name} &mdash; {warehouse.city}, {warehouse.state}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
