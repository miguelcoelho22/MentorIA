import { useState, type FormEvent } from 'react'
import { login } from '../services'

function Login({ onLogin }: { onLogin: () => void }) {
  const [email, setEmail] = useState('professor@demo.mentoria.test')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await login(email, password)
      onLogin()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login">
      <h1>MentorIA</h1>
      <p className="muted">Painel do professor</p>
      <form onSubmit={submit} className="card">
        <label>
          E-mail
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Senha
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={loading}>{loading ? 'Entrando...' : 'Entrar'}</button>
      </form>
    </div>
  )
}

export default Login
