import { useState, type FormEvent } from 'react'
import type { Navigate } from '../App'
import { getThemes, requestCorrection, sendEssay } from '../services'
import { useLoad } from '../utils'

function NewEssayPage({ go }: { go: Navigate }) {
  const { data: themes, error } = useLoad(getThemes)
  const [themeId, setThemeId] = useState('')
  const [text, setText] = useState('')
  const [correctNow, setCorrectNow] = useState(true)
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)

  const selected = themes?.find((t) => t.id === themeId)
  const words = text.trim() ? text.trim().split(/\s+/).length : 0

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setSendError(null)
    try {
      const essay = await sendEssay(themeId, text)
      if (correctNow) {
        const request = await requestCorrection(essay.id)
        go({ name: 'correction', requestId: request.id })
      } else {
        go({ name: 'essays' })
      }
    } catch (err) {
      setSendError((err as Error).message)
      setSending(false)
    }
  }

  if (error) return <p className="error">{error}</p>
  if (!themes) return <p className="muted">Carregando temas...</p>

  return (
    <>
      <h2>Nova redação</h2>
      <form onSubmit={submit} className="card column">
        <label>
          Tema
          <select value={themeId} onChange={(e) => setThemeId(e.target.value)} required>
            <option value="" disabled>Escolha um tema</option>
            {themes.map((theme) => (
              <option key={theme.id} value={theme.id}>{theme.title}</option>
            ))}
          </select>
        </label>
        {selected && <p className="muted small">{selected.description}</p>}

        <label>
          Texto
          <textarea rows={14} value={text} onChange={(e) => setText(e.target.value)} required />
        </label>
        <span className="muted small">{words} palavras</span>

        <label className="check">
          <input type="checkbox" checked={correctNow} onChange={(e) => setCorrectNow(e.target.checked)} />
          Pedir correção logo após enviar
        </label>

        {sendError && <p className="error">{sendError}</p>}
        <button type="submit" className="primary" disabled={sending}>
          {sending ? 'Enviando...' : 'Enviar redação'}
        </button>
      </form>
    </>
  )
}

export default NewEssayPage
