// Todas as chamadas que as telas fazem passam por aqui.
// Com VITE_MOCK=true (arquivo .env.development) usa os dados de exemplo do mock.ts;
// sem ela, chama o BFF do aluno através do gateway.
import { login as apiLogin, setToken, student } from './api'
import { mock } from './mock'
import type { CorrectionRequest, Essay, Feedback, Home, Plan, Quota, Theme } from './types'

export const USE_MOCK = import.meta.env.VITE_MOCK === 'true'

const CORRECTIONS_KEY = 'mentoria.student.corrections'

// O BFF não lista os pedidos de correção de uma redação,
// então o app guarda o último pedido feito para cada redação.
function savedCorrections(): Record<string, string> {
  try {
    const saved = localStorage.getItem(CORRECTIONS_KEY)
    if (saved) return JSON.parse(saved)
  } catch {
    // ignora e usa o padrão
  }
  return USE_MOCK ? { 'e-001': 'c-001' } : {}
}

function saveCorrection(essayId: string, requestId: string) {
  const all = savedCorrections()
  all[essayId] = requestId
  try {
    localStorage.setItem(CORRECTIONS_KEY, JSON.stringify(all))
  } catch {
    // sem localStorage o app continua funcionando, só não lembra o pedido
  }
}

export function correctionOf(essayId: string): string | undefined {
  return savedCorrections()[essayId]
}

export async function login(email: string, password: string): Promise<string> {
  if (USE_MOCK) {
    setToken('mock')
    return 'Ana Souza'
  }
  const result = await apiLogin(email, password)
  return result.user.name
}

export function getHome(): Promise<Home> {
  return USE_MOCK ? mock.home() : student<Home>('/home')
}

export function getThemes(): Promise<Theme[]> {
  return USE_MOCK ? mock.themes() : student<Theme[]>('/themes')
}

export function getEssays(): Promise<Essay[]> {
  return USE_MOCK ? mock.essays() : student<Essay[]>('/essays')
}

export function getEssay(essayId: string): Promise<Essay> {
  return USE_MOCK ? mock.essay(essayId) : student<Essay>(`/essays/${essayId}`)
}

export function sendEssay(themeId: string, text: string): Promise<Essay> {
  if (USE_MOCK) return mock.sendEssay(themeId, text)
  return student<Essay>('/essays', { method: 'POST', body: JSON.stringify({ themeId, text }) })
}

export async function requestCorrection(essayId: string): Promise<CorrectionRequest> {
  const request = USE_MOCK
    ? await mock.requestCorrection(essayId)
    : await student<CorrectionRequest>(`/essays/${essayId}/correction-requests`, { method: 'POST' })
  saveCorrection(essayId, request.id)
  return request
}

export function getCorrection(requestId: string): Promise<CorrectionRequest> {
  return USE_MOCK ? mock.correction(requestId) : student<CorrectionRequest>(`/correction-requests/${requestId}`)
}

export function getFeedback(requestId: string): Promise<Feedback> {
  return USE_MOCK ? mock.feedback(requestId) : student<Feedback>(`/feedbacks/${requestId}`)
}

export function getPlan(): Promise<Plan> {
  return USE_MOCK ? mock.plan() : student<Plan>('/plan')
}

export function completeTask(taskId: string): Promise<void> {
  if (USE_MOCK) return mock.completeTask(taskId)
  return student<void>(`/tasks/${taskId}`, { method: 'PATCH', body: JSON.stringify({ status: 'DONE' }) })
}

export function getQuota(): Promise<Quota> {
  return USE_MOCK ? mock.quota() : student<Quota>('/quota')
}
