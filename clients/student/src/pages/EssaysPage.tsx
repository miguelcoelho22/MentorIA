import { useState } from 'react'
import type { Navigate } from '../App'
import { correctionOf, getEssays, getThemes, requestCorrection } from '../services'
import { formatDate, useLoad } from '../utils'

function EssaysPage({ go }: { go: Navigate }) {
  const { data: essays, error } = useLoad(getEssays)
  const { data: themes } = useLoad(getThemes)
  const [sending, setSending] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  async function correct(essayId: string) {
    setSending(essayId)
    setActionError(null)
    try {
      const request = await requestCorrection(essayId)
      go({ name: 'correction', requestId: request.id })
    } catch (err) {
      setActionError((err as Error).message)
    } finally {
      setSending(null)
    }
  }

  function themeTitle(themeId: string) {
    return themes?.find((t) => t.id === themeId)?.title ?? themeId
  }

  if (error) return <p className="error">{error}</p>
  if (!essays) return <p className="muted">Carregando...</p>

  return (
    <>
      <div className="header-row">
        <h2>Minhas redações</h2>
        <button className="primary" onClick={() => go({ name: 'new-essay' })}>+ Nova</button>
      </div>

      {actionError && <p className="error">{actionError}</p>}
      {essays.length === 0 && <p className="muted">Você ainda não enviou nenhuma redação.</p>}

      <ul className="list">
        {essays.map((essay) => {
          const requestId = correctionOf(essay.id)
          return (
            <li key={essay.id} className="card column">
              <strong>{themeTitle(essay.themeId)}</strong>
              <span className="muted small">Enviada em {formatDate(essay.submittedAt)}</span>
              {requestId ? (
                <button className="link" onClick={() => go({ name: 'correction', requestId })}>
                  Ver correção →
                </button>
              ) : (
                <button onClick={() => correct(essay.id)} disabled={sending === essay.id}>
                  {sending === essay.id ? 'Enviando...' : 'Pedir correção'}
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}

export default EssaysPage
