import { useState } from 'react'
import { completeTask, getPlan } from '../services'
import { COMPETENCIES, formatDate, useLoad } from '../utils'

function PlanPage() {
  const { data: plan, error, reload } = useLoad(getPlan)
  const [saving, setSaving] = useState<string | null>(null)

  async function finish(taskId: string) {
    setSaving(taskId)
    try {
      await completeTask(taskId)
      reload()
    } finally {
      setSaving(null)
    }
  }

  if (error) return <p className="error">{error}</p>
  if (!plan) return <p className="muted">Carregando...</p>

  const pending = plan.tasks.filter((t) => t.status === 'PENDING')
  const done = plan.tasks.filter((t) => t.status === 'DONE')

  return (
    <>
      <h2>Plano de estudos</h2>
      <p className="muted small">Atualizado em {formatDate(plan.updatedAt)}</p>

      <h3>A fazer ({pending.length})</h3>
      <ul className="list">
        {pending.map((task) => (
          <li key={task.id} className="card">
            <div className="column">
              <span>{task.title}</span>
              <span className="tag">C{task.competency} · {COMPETENCIES[task.competency]}</span>
            </div>
            <button onClick={() => finish(task.id)} disabled={saving === task.id}>
              {saving === task.id ? '...' : 'Concluir'}
            </button>
          </li>
        ))}
      </ul>

      <h3>Concluídas ({done.length})</h3>
      <ul className="list">
        {done.map((task) => (
          <li key={task.id} className="card done">
            <span>✓ {task.title}</span>
          </li>
        ))}
      </ul>
    </>
  )
}

export default PlanPage
