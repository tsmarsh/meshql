import { useState } from 'react'
import FarmSetup from './components/FarmSetup'
import LogEggs from './components/LogEggs'
import MyHens from './components/MyHens'
import MyCoops from './components/MyCoops'
import WeeklySummary from './components/WeeklySummary'

const TABS = [
  { key: 'log', label: 'Log Eggs' },
  { key: 'hens', label: 'My Hens' },
  { key: 'coops', label: 'My Coops' },
  { key: 'summary', label: 'Summary' },
]

export default function App() {
  const [farmId, setFarmId] = useState(() => localStorage.getItem('farmId'))
  const [farmName, setFarmName] = useState(() => localStorage.getItem('farmName') || '')
  const [activeTab, setActiveTab] = useState('log')

  function handleFarmSelect(id, name) {
    localStorage.setItem('farmId', id)
    localStorage.setItem('farmName', name)
    setFarmId(id)
    setFarmName(name)
  }

  function handleReset() {
    localStorage.removeItem('farmId')
    localStorage.removeItem('farmName')
    setFarmId(null)
    setFarmName('')
  }

  if (!farmId) {
    return <FarmSetup onSelect={handleFarmSelect} />
  }

  return (
    <div className="min-h-screen bg-amber-50 flex flex-col">
      {/* Header */}
      <header className="bg-amber-800 text-white px-4 py-3 shadow-lg">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold">Homesteader</h1>
            <p className="text-amber-200 text-sm">{farmName}</p>
          </div>
          <button
            onClick={handleReset}
            className="text-amber-300 text-xs hover:text-white transition-colors"
          >
            Switch Farm
          </button>
        </div>
      </header>

      {/* Content */}
      <main className="flex-1 overflow-y-auto pb-20 px-4 py-4">
        {activeTab === 'log' && <LogEggs farmId={farmId} />}
        {activeTab === 'hens' && <MyHens farmId={farmId} />}
        {activeTab === 'coops' && <MyCoops farmId={farmId} />}
        {activeTab === 'summary' && <WeeklySummary farmId={farmId} />}
      </main>

      {/* Bottom Tab Bar */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-amber-200 shadow-lg">
        <div className="flex">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex-1 py-3 text-center text-sm font-medium transition-colors ${
                activeTab === tab.key
                  ? 'text-amber-800 bg-amber-100 border-t-2 border-amber-700'
                  : 'text-gray-500 hover:text-amber-700'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </nav>
    </div>
  )
}
