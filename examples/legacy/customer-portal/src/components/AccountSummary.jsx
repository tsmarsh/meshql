const STATUS_STYLES = {
  active:    { bg: 'bg-green-100',  text: 'text-green-700',  label: 'Active' },
  suspended: { bg: 'bg-red-100',    text: 'text-red-700',    label: 'Suspended' },
  closed:    { bg: 'bg-gray-100',   text: 'text-gray-700',   label: 'Closed' },
}

export default function AccountSummary({ customer }) {
  const status = STATUS_STYLES[customer.status] || STATUS_STYLES.active

  return (
    <div className="bg-white rounded-xl shadow-md p-6">
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            {customer.first_name} {customer.last_name}
          </h2>
          <p className="text-sm text-gray-500 font-mono mt-1">
            Account #{customer.account_number}
          </p>
        </div>
        <span className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${status.bg} ${status.text}`}>
          {status.label}
        </span>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-4 text-sm">
        <div>
          <span className="text-gray-500">Rate Class</span>
          <p className="font-medium text-gray-900">{customer.rate_class}</p>
        </div>
        <div>
          <span className="text-gray-500">Service Type</span>
          <p className="font-medium text-gray-900">{customer.service_type}</p>
        </div>
        <div className="col-span-2">
          <span className="text-gray-500">Service Address</span>
          <p className="font-medium text-gray-900">
            {customer.address}, {customer.city}, {customer.state} {customer.zip}
          </p>
        </div>
        <div>
          <span className="text-gray-500">Connected Since</span>
          <p className="font-medium text-gray-900">{customer.connected_date}</p>
        </div>
        <div>
          <span className="text-gray-500">Contact</span>
          <p className="font-medium text-gray-900">{customer.phone}</p>
          {customer.email && (
            <p className="text-gray-600">{customer.email}</p>
          )}
        </div>
        <div>
          <span className="text-gray-500">Budget Billing</span>
          <p className="font-medium text-gray-900">{customer.budget_billing ? 'Enrolled' : 'Not enrolled'}</p>
        </div>
        <div>
          <span className="text-gray-500">Paperless Billing</span>
          <p className="font-medium text-gray-900">{customer.paperless ? 'Enrolled' : 'Not enrolled'}</p>
        </div>
      </div>
    </div>
  )
}
