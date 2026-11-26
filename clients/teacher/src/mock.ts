// Dados de exemplo usados enquanto o BFF do professor não está pronto.
// Seguem o formato definido em docs/assets/arquitetura/05-bff.md.
import type { ClassOverview, ClassSummary, StudentDetail } from './types'

const classes: ClassSummary[] = [
  { id: 'demo-3A', name: '3º ano A', studentCount: 32 },
  { id: 'demo-3B', name: '3º ano B', studentCount: 28 },
]

const overviews: Record<string, ClassOverview> = {
  'demo-3A': {
    classId: 'demo-3A',
    students: 32,
    averageScore: 588,
    weakestCompetency: 5,
    needSupport: [
      { studentId: 'a3c9', name: 'Bruno Silva', lastScore: 420, trend: 'down', pendingTasks: 6 },
      { studentId: 'b7f2', name: 'Carla Mendes', lastScore: 480, trend: 'stable', pendingTasks: 4 },
      { studentId: 'c1d4', name: 'Diego Rocha', lastScore: 520, trend: 'up', pendingTasks: 3 },
    ],
  },
  'demo-3B': {
    classId: 'demo-3B',
    students: 28,
    averageScore: 612,
    weakestCompetency: 3,
    needSupport: [
      { studentId: 'd9e1', name: 'Eduarda Lima', lastScore: 460, trend: 'down', pendingTasks: 5 },
    ],
  },
}

function detail(studentId: string, name: string, lastScore: number): StudentDetail {
  return {
    studentId,
    name,
    progress: {
      lastScore,
      trend: 'down',
      weakestCompetency: 5,
      averageByCompetency: [
        { id: 1, average: 120 },
        { id: 2, average: 110 },
        { id: 3, average: 90 },
        { id: 4, average: 80 },
        { id: 5, average: 40 },
      ],
      tasksDone: 2,
      tasksPending: 6,
    },
    essays: [
      { essayId: 'e-210', theme: 'Impactos do uso excessivo de telas na adolescência', submittedAt: '2026-11-18T10:00:00Z', totalScore: lastScore },
      { essayId: 'e-150', theme: 'Desafios para a valorização da leitura no Brasil', submittedAt: '2026-11-04T10:00:00Z', totalScore: lastScore + 60 },
      { essayId: 'e-260', theme: 'Caminhos para combater a insegurança alimentar', submittedAt: '2026-11-24T10:00:00Z', totalScore: null },
    ],
  }
}

function wait<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 300))
}

export const mock = {
  classes: () => wait(classes),

  overview(classId: string) {
    const overview = overviews[classId]
    return overview ? wait(overview) : Promise.reject(new Error('Turma não encontrada.'))
  },

  student(studentId: string) {
    const found = Object.values(overviews)
      .flatMap((o) => o.needSupport)
      .find((s) => s.studentId === studentId)
    return found
      ? wait(detail(found.studentId, found.name, found.lastScore))
      : Promise.reject(new Error('Aluno não encontrado.'))
  },
}
