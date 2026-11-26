// Todas as chamadas que as telas fazem passam por aqui.
// Com VITE_MOCK=true (arquivo .env.development) usa os dados de exemplo do mock.ts;
// sem ela, chama o BFF do professor através do gateway.
import { login as apiLogin, setToken, teacher } from './api'
import { mock } from './mock'
import type { ClassOverview, ClassSummary, StudentDetail } from './types'

export const USE_MOCK = import.meta.env.VITE_MOCK === 'true'

export async function login(email: string, password: string): Promise<string> {
  if (USE_MOCK) {
    setToken('mock')
    return 'Marcos Lima'
  }
  const result = await apiLogin(email, password)
  return result.user.name
}

export function getClasses(): Promise<ClassSummary[]> {
  return USE_MOCK ? mock.classes() : teacher<ClassSummary[]>('/classes')
}

export function getOverview(classId: string): Promise<ClassOverview> {
  return USE_MOCK ? mock.overview(classId) : teacher<ClassOverview>(`/classes/${classId}/overview`)
}

export function getStudent(studentId: string): Promise<StudentDetail> {
  return USE_MOCK ? mock.student(studentId) : teacher<StudentDetail>(`/students/${studentId}`)
}
