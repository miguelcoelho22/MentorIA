const API_BASE = '/api/v1'
const TOKEN_KEY = 'mentoria.student.token'

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { id: string; name: string; role: string }
}

export class ApiError extends Error {
  readonly status: number
  readonly correlationId: string | null

  constructor(status: number, message: string, correlationId: string | null) {
    super(message)
    this.status = status
    this.correlationId = correlationId
  }
}

function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  const correlationId = response.headers.get('X-Correlation-Id')

  if (response.status === 401) setToken(null)
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const message = problem?.detail ?? problem?.title ?? `Erro ${response.status}`
    throw new ApiError(response.status, message, correlationId)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const result = await api<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  if (result.user.role !== 'STUDENT') {
    throw new ApiError(403, 'Esta conta não tem acesso a este aplicativo.', null)
  }
  setToken(result.accessToken)
  return result
}

export function logout() {
  setToken(null)
}

export function isLoggedIn(): boolean {
  return getToken() !== null
}

export function student<T>(path: string, init?: RequestInit): Promise<T> {
  return api<T>(`/student${path}`, init)
}
