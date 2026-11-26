import type { Navigate } from '../App'
import { getHome } from '../services'
import { COMPETENCIES, formatDate, trendLabel, useLoad } from '../utils'

function HomePage({ go }: { go: Navigate }) {
  const { data: home, error } = useLoad(getHome)

  if (error) return <p className="error">{error}</p>
  if (!home) return <p className="muted">Carregando...</p>

  return (
    <>
      <h2>Olá! O que vamos fazer hoje?</h2>

      <div className="row">
        <div className="card stat">
          <span className="muted">Última nota</span>
          <strong className="big">{home.progress.lastScore}</strong>
          <span className={`trend ${home.progress.trend}`}>{trendLabel(home.progress.trend)}</span>
        </div>
        <div className="card stat">
          <span className="muted">Correções disponíveis</span>
          {home.quota ? (
            <>
              <strong className="big">{home.quota.available}</strong>
              <span className="muted small">renova em {formatDate(home.quota.renewsOn)}</span>
            </>
          ) : (
            <span className="muted small">indisponível no momento</span>
          )}
        </div>
      </div>

      {home._links?.newEssay && (
        <button className="primary wide" onClick={() => go({ name: 'new-essay' })}>
          Escrever nova redação
        </button>
      )}

      <h3>Próximas tarefas</h3>
      {home.nextTasks.length === 0 && <p className="muted">Nenhuma tarefa pendente. 🎉</p>}
      <ul className="list">
        {home.nextTasks.map((task) => (
          <li key={task.id} className="card">
            <span>{task.title}</span>
            <span className="tag">C{task.competency} · {COMPETENCIES[task.competency]}</span>
          </li>
        ))}
      </ul>
      {home.nextTasks.length > 0 && (
        <button className="link" onClick={() => go({ name: 'plan' })}>Ver plano completo →</button>
      )}
    </>
  )
}

export default HomePage
