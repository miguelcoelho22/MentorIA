import { useState } from 'react'
import { isLoggedIn, logout } from './api'
import { getClasses, USE_MOCK } from './services'
import { useLoad } from './utils'
import Login from './pages/Login'
import ClassPage from './pages/ClassPage'
import StudentPage from './pages/StudentPage'
import './App.css'

export type Page =
  | { name: 'class'; classId: string }
  | { name: 'student'; classId: string; studentId: string }

function Panel({ onLogout }: { onLogout: () => void }) {
  const { data: classes, error } = useLoad(getClasses)
  const [page, setPage] = useState<Page | null>(null)

  const current = page ?? (classes && classes.length > 0 ? { name: 'class' as const, classId: classes[0].id } : null)

  return (
    <div className="layout">
      <aside className="sidebar">
        <h1>MentorIA</h1>
        <p className="muted small">Painel do professor</p>
        {USE_MOCK && <span className="badge">dados de exemplo</span>}

        <h3>Minhas turmas</h3>
        {error && <p className="error">{error}</p>}
        <ul className="menu">
          {classes?.map((c) => (
            <li key={c.id}>
              <button
                className={current?.classId === c.id ? 'active' : ''}
                onClick={() => setPage({ name: 'class', classId: c.id })}
              >
                {c.name}
                <span className="muted small">{c.studentCount} alunos</span>
              </button>
            </li>
          ))}
        </ul>

        <button className="link logout" onClick={onLogout}>Sair</button>
      </aside>

      <main className="main">
        {!current && !error && <p className="muted">Carregando...</p>}
        {current?.name === 'class' && (
          <ClassPage
            classId={current.classId}
            className={classes?.find((c) => c.id === current.classId)?.name ?? current.classId}
            openStudent={(studentId) => setPage({ name: 'student', classId: current.classId, studentId })}
          />
        )}
        {current?.name === 'student' && (
          <StudentPage
            studentId={current.studentId}
            back={() => setPage({ name: 'class', classId: current.classId })}
          />
        )}
      </main>
    </div>
  )
}

function App() {
  const [logged, setLogged] = useState(isLoggedIn())

  if (!logged) return <Login onLogin={() => setLogged(true)} />

  return (
    <Panel
      onLogout={() => {
        logout()
        setLogged(false)
      }}
    />
  )
}

export default App
