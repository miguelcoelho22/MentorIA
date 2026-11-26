export type Trend = 'up' | 'down' | 'stable'

export type ClassSummary = {
  id: string
  name: string
  studentCount: number
}

export type StudentNeedingSupport = {
  studentId: string
  name: string
  lastScore: number
  trend: Trend
  pendingTasks: number
}

export type ClassOverview = {
  classId: string
  students: number
  averageScore: number
  weakestCompetency: number
  needSupport: StudentNeedingSupport[]
}

export type StudentDetail = {
  studentId: string
  name: string
  progress: {
    lastScore: number
    trend: Trend
    weakestCompetency?: number
    averageByCompetency: { id: number; average: number }[]
    tasksDone: number
    tasksPending: number
  }
  essays: {
    essayId: string
    theme: string
    submittedAt: string
    totalScore: number | null
  }[]
}
