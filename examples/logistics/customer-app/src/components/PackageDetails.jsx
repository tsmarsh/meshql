export default function PackageDetails({ pkg }) {
  return (
    <div className="bg-white rounded-xl shadow-md p-6">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">{pkg.description}</h2>
          <p className="text-sm text-gray-500 font-mono mt-1">{pkg.tracking_number}</p>
        </div>
        <span className="text-2xl">📦</span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 text-sm">
        <div>
          <span className="text-gray-500">Recipient</span>
          <p className="font-medium text-gray-900">{pkg.recipient}</p>
        </div>
        <div>
          <span className="text-gray-500">Weight</span>
          <p className="font-medium text-gray-900">{pkg.weight} lbs</p>
        </div>
        <div className="col-span-2">
          <span className="text-gray-500">Delivery Address</span>
          <p className="font-medium text-gray-900">{pkg.recipient_address}</p>
        </div>
      </div>
    </div>
  )
}
