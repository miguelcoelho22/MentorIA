import { useState } from 'react'
import { isLoggedIn, logout } from './api'
import { USE_MOCK } from './services'
import Login from './pages/Login'
import HomePage from './pages/HomePage'
import EssaysPage from './pages/EssaysPage'
import NewEssayPage from './pages/NewEssayPage'
import CorrectionPage from './pages/CorrectionPage'
import PlanPage from './pages/PlanPage'
import './App.css'

export type Page =
  | { name: 'home' }
  | { name: 'essays' }
  | { name: 'new-essay' }
  | { name: 'correction'; requestId: string }
  | { name: 'plan' }

export type Navigate = (page: Page) => void

function App() {
  const [logged, setLogged] = useState(isLoggedIn())
  const [page, setPage] = useState<Page>({ name: 'home' })

  if (!logged) {
    return <Login onLogin={() => setLogged(true)} />
  }

  function exit() {
    logout()
    setLogged(false)
    setPage({ name: 'home' })
  }

  return (
    <div className="app">
      <header className="topbar">
        <strong>MentorIA</strong>
        {USE_MOCK && <span className="badge">dados de exemplo</span>}
        <button className="link" onClick={exit}>Sair</button>
      </header>

      <main className="content">
        {page.name === 'home' && <HomePage go={setPage} />}
        {page.name === 'essays' && <EssaysPage go={setPage} />}
        {page.name === 'new-essay' && <NewEssayPage go={setPage} />}
        {page.name === 'correction' && <CorrectionPage requestId={page.requestId} go={setPage} />}
        {page.name === 'plan' && <PlanPage />}
      </main>

      <nav className="tabbar">
        <button className={page.name === 'home' ? 'active' : ''} onClick={() => setPage({ name: 'home' })}>
          Início
        </button>
        <button
          className={page.name === 'essays' || page.name === 'new-essay' || page.name === 'correction' ? 'active' : ''}
          onClick={() => setPage({ name: 'essays' })}
        >
          Redações
        </button>
        <button className={page.name === 'plan' ? 'active' : ''} onClick={() => setPage({ name: 'plan' })}>
          Plano
        </button>
      </nav>
    </div>
  )
}

export default App
