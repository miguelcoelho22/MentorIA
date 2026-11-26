import { useEffect, useState } from 'react'
import type { Navigate } from '../App'
import { getCorrection, getFeedback } from '../services'
import type { CorrectionRequest, Feedback } from '../types'
import { COMPETENCIES } from '../utils'

const STEPS = ['PENDING', 'PROCESSING', 'COMPLETED']

const STATUS_TEXT: Record<string, string> = {
  PENDING: 'Pedido recebido, verificando sua cota...',
  PROCESSING: 'Sua redação está sendo corrigida...',
  COMPLETED: 'Correção concluída!',
  REJECTED: 'Pedido recusado.',
  FAILED: 'Não foi possível corrigir agora.',
}

const REASON_TEXT: Record<string, string> = {
  QUOTA_EXHAUSTED: 'Você já usou todas as correções deste mês. Sua cota foi devolvida.',
  AI_UNAVAILABLE: 'O corretor está indisponível no momento. Sua cota foi devolvida, tente de novo mais tarde.',
  PUBLICATION_ERROR: 'Houve um erro ao publicar o resultado. Sua cota foi devolvida.',
}

function CorrectionPage({ requestId, go }: { requestId: string; go: Navigate }) {
  const [request, setRequest] = useState<CorrectionRequest | null>(null)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [error, setError] = useState<string | null>(null)

  // A correção é assíncrona (SAGA): consulta o estado a cada 2 segundos até terminar.
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout>

    async function check() {
      try {
        const current = await getCorrection(requestId)
        if (!active) return
        setRequest(current)
        if (current.status === 'COMPLETED') {
          setFeedback(await getFeedback(requestId))
        } else if (current.status === 'PENDING' || current.status === 'PROCESSING') {
          timer = setTimeout(check, 2000)
        }
      } catch (err) {
        if (active) setError((err as Error).message)
      }
    }

    check()
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [requestId])

  if (error) return <p className="error">{error}</p>
  if (!request) return <p className="muted">Carregando...</p>

  const failed = request.status === 'REJECTED' || request.status === 'FAILED'
  const step = STEPS.indexOf(request.status)

  return (
    <>
      <h2>Correção</h2>

      <div className="card column">
        <strong>{STATUS_TEXT[request.status]}</strong>
        {!failed && (
          <div className="steps">
            {['Recebido', 'Corrigindo', 'Pronto'].map((label, i) => (
              <span key={label} className={i <= step ? 'done' : ''}>{label}</span>
            ))}
          </div>
        )}
        {failed && request.reason && <p className="error">{REASON_TEXT[request.reason]}</p>}
        {request._links?.retry && <button onClick={() => go({ name: 'essays' })}>Tentar de novo</button>}
      </div>

      {feedback && (
        <>
          <div className="card stat">
            <span className="muted">Nota total</span>
            <strong className="big">{feedback.totalScore}</strong>
            <span className="muted small">de 1000</span>
          </div>

          <h3>Por competência</h3>
          <ul className="list">
            {feedback.competencies.map((c) => (
              <li key={c.id} className="card column">
                <div className="header-row">
                  <strong>C{c.id} · {COMPETENCIES[c.id]}</strong>
                  <span className="tag">{c.score}/200</span>
                </div>
                <div className="bar"><div style={{ width: `${(c.score / 200) * 100}%` }} /></div>
                <span className="small">{c.comment}</span>
              </li>
            ))}
          </ul>

          {request._links?.['study-plan'] && (
            <button className="primary wide" onClick={() => go({ name: 'plan' })}>
              Ver plano de estudos atualizado
            </button>
          )}
        </>
      )}
    </>
  )
}

export default CorrectionPage
