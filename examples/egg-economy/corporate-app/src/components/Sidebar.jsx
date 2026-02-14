const NAV_ITEMS = [
  { key: 'dashboard', label: 'Dashboard', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
  { key: 'coops', label: 'Coops', icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4' },
  { key: 'hens', label: 'Hens', icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z' },
  { key: 'events', label: 'Events', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01' },
  { key: 'containers', label: 'Containers', icon: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4' },
]

export default function Sidebar({ section, onNavigate, farms, selectedFarmId, onSelectFarm }) {
  return (
    <aside className="fixed left-0 top-0 bottom-0 w-64 bg-slate-800 text-white flex flex-col">
      {/* Header */}
      <div className="px-5 py-5 border-b border-slate-700">
        <h1 className="text-lg font-bold">Egg Economy</h1>
        <p className="text-slate-400 text-xs mt-0.5">Corporate Portal</p>
      </div>

      {/* Farm Selector */}
      <div className="px-4 py-3 border-b border-slate-700">
        <label className="block text-xs text-slate-400 mb-1">Farm</label>
        <select
          value={selectedFarmId || ''}
          onChange={(e) => onSelectFarm(e.target.value)}
          className="w-full bg-slate-700 text-white text-sm rounded-lg px-3 py-2 border-none focus:ring-2 focus:ring-blue-500"
        >
          {farms.map((f) => (
            <option key={f.id} value={f.id}>{f.name}</option>
          ))}
        </select>
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.key}
            onClick={() => onNavigate(item.key)}
            className={`w-full flex items-center gap-3 px-5 py-2.5 text-sm font-medium transition-colors ${
              section === item.key
                ? 'bg-blue-600 text-white'
                : 'text-slate-300 hover:bg-slate-700 hover:text-white'
            }`}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d={item.icon} />
            </svg>
            {item.label}
          </button>
        ))}
      </nav>

      {/* Footer */}
      <div className="px-5 py-4 border-t border-slate-700 text-xs text-slate-500">
        Powered by <a href="https://github.com/tsmarsh/meshql" className="text-blue-400 hover:underline" target="_blank" rel="noopener noreferrer">MeshQL</a>
      </div>
    </aside>
  )
}
