// Dados de exemplo usados enquanto o BFF do aluno não está pronto.
// Seguem o formato definido em docs/assets/arquitetura/05-bff.md.
import type { CorrectionRequest, Essay, Feedback, Home, Plan, Quota, Theme } from './types'

const themes: Theme[] = [
  {
    id: 'tema-01',
    title: 'Desafios para a valorização da leitura no Brasil',
    description: 'Discuta os obstáculos que afastam a população da leitura e proponha caminhos.',
  },
  {
    id: 'tema-02',
    title: 'Impactos do uso excessivo de telas na adolescência',
    description: 'Analise os efeitos do tempo de tela na saúde e no aprendizado dos jovens.',
  },
  {
    id: 'tema-03',
    title: 'Caminhos para combater a insegurança alimentar',
    description: 'Reflita sobre as causas da fome no país e as políticas para enfrentá-la.',
  },
]

const essays: Essay[] = [
  {
    id: 'e-001',
    themeId: 'tema-01',
    text: 'A leitura, no Brasil, ainda é um hábito restrito a uma parcela pequena da população...',
    submittedAt: '2026-11-10T14:20:00Z',
  },
]

const requests: (CorrectionRequest & { createdMs: number })[] = [
  {
    id: 'c-001',
    essayId: 'e-001',
    status: 'COMPLETED',
    requestedAt: '2026-11-10T14:21:00Z',
    createdMs: 0,
  },
]

const feedbacks: Feedback[] = [
  {
    requestId: 'c-001',
    essayId: 'e-001',
    totalScore: 640,
    publishedAt: '2026-11-10T14:23:00Z',
    competencies: [
      { id: 1, score: 160, comment: 'Poucos desvios gramaticais. Revise a concordância verbal.' },
      { id: 2, score: 140, comment: 'Tema compreendido, mas o repertório pode ser mais variado.' },
      { id: 3, score: 120, comment: 'Argumentos claros, porém pouco aprofundados.' },
      { id: 4, score: 120, comment: 'Use mais conectivos entre os parágrafos.' },
      { id: 5, score: 100, comment: 'Proposta de intervenção sem detalhamento do meio de execução.' },
    ],
  },
]

const plan: Plan = {
  updatedAt: '2026-11-10T14:24:00Z',
  tasks: [
    { id: 't-18', title: 'Proposta de intervenção: 2 exercícios', competency: 5, status: 'PENDING' },
    { id: 't-19', title: 'Conectivos entre parágrafos', competency: 4, status: 'PENDING' },
    { id: 't-20', title: 'Aprofundar argumentos com dados', competency: 3, status: 'PENDING' },
    { id: 't-15', title: 'Revisão de concordância verbal', competency: 1, status: 'DONE' },
  ],
}

const quota: Quota = { period: '2026-11', limit: 4, reserved: 0, consumed: 1, available: 3 }

let counter = 1

function wait<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 300))
}

// A correção "anda" sozinha com o tempo, simulando a SAGA.
function refresh(request: CorrectionRequest & { createdMs: number }) {
  if (request.status === 'COMPLETED' || request.createdMs === 0) return
  const elapsed = Date.now() - request.createdMs
  if (elapsed > 6000) {
    request.status = 'COMPLETED'
    quota.reserved -= 1
    quota.consumed += 1
    feedbacks.push({
      requestId: request.id,
      essayId: request.essayId,
      totalScore: 700,
      publishedAt: new Date().toISOString(),
      competencies: [
        { id: 1, score: 160, comment: 'Bom domínio da norma culta.' },
        { id: 2, score: 160, comment: 'Tema bem desenvolvido.' },
        { id: 3, score: 140, comment: 'Argumentação consistente.' },
        { id: 4, score: 120, comment: 'Atenção à repetição de palavras.' },
        { id: 5, score: 120, comment: 'Detalhe melhor o agente da proposta.' },
      ],
    })
  } else if (elapsed > 2000) {
    request.status = 'PROCESSING'
  }
}

function withLinks(request: CorrectionRequest): CorrectionRequest {
  const links: Record<string, { href: string }> = {
    self: { href: `/api/v1/student/correction-requests/${request.id}` },
    essay: { href: `/api/v1/student/essays/${request.essayId}` },
  }
  if (request.status === 'COMPLETED') {
    links.feedback = { href: `/api/v1/student/feedbacks/${request.id}` }
    links['study-plan'] = { href: '/api/v1/student/plan' }
  }
  return { ...request, _links: links }
}

export const mock = {
  home(): Promise<Home> {
    const pending = plan.tasks.filter((t) => t.status === 'PENDING').slice(0, 3)
    const last = feedbacks[feedbacks.length - 1]
    return wait({
      quota: { available: quota.available, renewsOn: '2026-12-01' },
      nextTasks: pending.map((t) => ({ id: t.id, title: t.title, competency: t.competency, done: false })),
      progress: { lastScore: last.totalScore, trend: feedbacks.length > 1 ? 'up' : 'stable' },
      _links: { newEssay: { href: '/api/v1/student/essays' } },
    })
  },

  themes: () => wait(themes),

  essays: () => wait(essays.map(({ text: _text, ...rest }) => rest)),

  essay(id: string) {
    const essay = essays.find((e) => e.id === id)
    return essay ? wait(essay) : Promise.reject(new Error('Redação não encontrada.'))
  },

  sendEssay(themeId: string, text: string): Promise<Essay> {
    const essay: Essay = { id: `e-${100 + counter++}`, themeId, text, submittedAt: new Date().toISOString() }
    essays.unshift(essay)
    return wait(essay)
  },

  requestCorrection(essayId: string): Promise<CorrectionRequest> {
    if (quota.available === 0) {
      const rejected = { id: `c-${100 + counter++}`, essayId, status: 'REJECTED' as const,
        requestedAt: new Date().toISOString(), reason: 'QUOTA_EXHAUSTED' as const, createdMs: 0 }
      requests.push(rejected)
      return wait(withLinks(rejected))
    }
    quota.available -= 1
    quota.reserved += 1
    const request = { id: `c-${100 + counter++}`, essayId, status: 'PENDING' as const,
      requestedAt: new Date().toISOString(), createdMs: Date.now() }
    requests.push(request)
    return wait(withLinks(request))
  },

  correction(id: string) {
    const request = requests.find((r) => r.id === id)
    if (!request) return Promise.reject(new Error('Pedido de correção não encontrado.'))
    refresh(request)
    const { createdMs: _createdMs, ...rest } = request
    return wait(withLinks(rest))
  },

  feedback(requestId: string) {
    const feedback = feedbacks.find((f) => f.requestId === requestId)
    return feedback ? wait(feedback) : Promise.reject(new Error('Feedback ainda não publicado.'))
  },

  plan: () => wait(plan),

  completeTask(taskId: string) {
    const task = plan.tasks.find((t) => t.id === taskId)
    if (task) task.status = 'DONE'
    return wait(undefined)
  },

  quota: () => wait(quota),
}
