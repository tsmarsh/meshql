import { useState, useEffect } from 'react'
import { graphqlQuery } from './api'
import Sidebar from './components/Sidebar'
import Dashboard from './components/Dashboard'
import CoopManager from './components/CoopManager'
import HenManager from './components/HenManager'
import EventLog from './components/EventLog'
import ContainerView from './components/ContainerView'

export default function App() {
  const [section, setSection] = useState('dashboard')
  const [farms, setFarms] = useState([])
  const [selectedFarmId, setSelectedFarmId] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    graphqlQuery('/farm/graph', '{ getAll { id name farm_type zone owner } }', 'getAll')
      .then((data) => {
        const f = data || []
        setFarms(f)
        if (f.length > 0) setSelectedFarmId(f[0].id)
      })
      .finally(() => setLoading(false))
  }, [])

  const selectedFarm = farms.find(f => f.id === selectedFarmId)

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center text-slate-500">
          <div className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
          <p className="mt-3">Loading...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 flex">
      <Sidebar
        section={section}
        onNavigate={setSection}
        farms={farms}
        selectedFarmId={selectedFarmId}
        onSelectFarm={setSelectedFarmId}
      />
      <main className="flex-1 ml-64 p-6">
        {section === 'dashboard' && <Dashboard farmId={selectedFarmId} farmName={selectedFarm?.name} />}
        {section === 'coops' && <CoopManager farmId={selectedFarmId} />}
        {section === 'hens' && <HenManager farmId={selectedFarmId} />}
        {section === 'events' && <EventLog farmId={selectedFarmId} />}
        {section === 'containers' && <ContainerView />}
      </main>
    </div>
  )
}
