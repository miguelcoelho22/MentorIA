export type Trend = 'up' | 'down' | 'stable'

export type HomeTask = {
  id: string
  title: string
  competency: number
  done: boolean
}

export type Home = {
  quota: { available: number; renewsOn: string } | null
  nextTasks: HomeTask[]
  progress: { lastScore: number; trend: Trend }
  _links?: Record<string, { href: string }>
}

export type Theme = {
  id: string
  title: string
  description: string
}

export type Essay = {
  id: string
  themeId: string
  text?: string
  submittedAt: string
}

export type CorrectionStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED' | 'FAILED'

export type CorrectionRequest = {
  id: string
  essayId: string
  status: CorrectionStatus
  requestedAt: string
  reason?: 'QUOTA_EXHAUSTED' | 'AI_UNAVAILABLE' | 'PUBLICATION_ERROR'
  _links?: Record<string, { href: string }>
}

export type Feedback = {
  requestId: string
  essayId: string
  totalScore: number
  publishedAt?: string
  competencies: { id: number; score: number; comment: string }[]
}

export type PlanTask = {
  id: string
  title: string
  competency: number
  status: 'PENDING' | 'DONE'
}

export type Plan = {
  updatedAt: string
  tasks: PlanTask[]
}

export type Quota = {
  period: string
  limit: number
  reserved: number
  consumed: number
  available: number
}
