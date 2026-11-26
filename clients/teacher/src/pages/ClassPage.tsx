import { getOverview } from '../services'
import { COMPETENCIES, trendLabel, useLoad } from '../utils'

type Props = {
  classId: string
  className: string
  openStudent: (studentId: string) => void
}

function ClassPage({ classId, className, openStudent }: Props) {
  const { data: overview, error } = useLoad(() => getOverview(classId), [classId])

  if (error) return <p className="error">{error}</p>
  if (!overview || overview.classId !== classId) return <p className="muted">Carregando...</p>

  return (
    <>
      <h2>{className}</h2>

      <div className="cards">
        <div className="card">
          <span className="muted">Alunos</span>
          <strong className="big">{overview.students}</strong>
        </div>
        <div className="card">
          <span className="muted">Média da turma</span>
          <strong className="big">{overview.averageScore}</strong>
        </div>
        <div className="card">
          <span className="muted">Competência mais fraca</span>
          {overview.weakestCompetency === null ? (
            <strong className="small">Sem notas</strong>
          ) : (
            <>
              <strong className="big">C{overview.weakestCompetency}</strong>
              <span className="small">{COMPETENCIES[overview.weakestCompetency]}</span>
            </>
          )}
        </div>
      </div>

      <h3>Alunos que precisam de apoio</h3>
      {overview.needSupport.length === 0 ? (
        <p className="muted">Nenhum aluno precisando de apoio no momento.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Aluno</th>
              <th>Última nota</th>
              <th>Tendência</th>
              <th>Tarefas pendentes</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {overview.needSupport.map((s) => (
              <tr key={s.studentId}>
                <td>{s.name}</td>
                <td>{s.lastScore}</td>
                <td className={`trend ${s.trend}`}>{trendLabel(s.trend)}</td>
                <td>{s.pendingTasks}</td>
                <td>
                  <button className="link" onClick={() => openStudent(s.studentId)}>Ver aluno →</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

export default ClassPage
