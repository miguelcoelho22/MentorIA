import { getStudent } from '../services'
import { COMPETENCIES, formatDate, trendLabel, useLoad } from '../utils'

function StudentPage({ studentId, back }: { studentId: string; back: () => void }) {
  const { data: student, error } = useLoad(() => getStudent(studentId), [studentId])

  if (error) return <p className="error">{error}</p>
  if (!student) return <p className="muted">Carregando...</p>

  const { progress } = student

  return (
    <>
      <button className="link" onClick={back}>← Voltar para a turma</button>
      <h2>{student.name}</h2>

      <div className="cards">
        <div className="card">
          <span className="muted">Última nota</span>
          <strong className="big">{progress.lastScore}</strong>
          <span className={`trend ${progress.trend}`}>{trendLabel(progress.trend)}</span>
        </div>
        <div className="card">
          <span className="muted">Tarefas</span>
          <strong className="big">{progress.tasksDone}/{progress.tasksDone + progress.tasksPending}</strong>
          <span className="small">concluídas</span>
        </div>
        {progress.weakestCompetency && (
          <div className="card">
            <span className="muted">Precisa reforçar</span>
            <strong className="big">C{progress.weakestCompetency}</strong>
            <span className="small">{COMPETENCIES[progress.weakestCompetency]}</span>
          </div>
        )}
      </div>

      <h3>Média por competência</h3>
      <div className="card competencies">
        {progress.averageByCompetency.map((c) => (
          <div key={c.id} className="competency">
            <span>C{c.id} · {COMPETENCIES[c.id]}</span>
            <div className="bar"><div style={{ width: `${(c.average / 200) * 100}%` }} /></div>
            <span className="small">{c.average}/200</span>
          </div>
        ))}
      </div>

      <h3>Redações</h3>
      <table>
        <thead>
          <tr>
            <th>Tema</th>
            <th>Enviada em</th>
            <th>Nota</th>
          </tr>
        </thead>
        <tbody>
          {student.essays.map((e) => (
            <tr key={e.essayId}>
              <td>{e.theme}</td>
              <td>{formatDate(e.submittedAt)}</td>
              <td>{e.totalScore ?? <span className="muted">aguardando correção</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

export default StudentPage
