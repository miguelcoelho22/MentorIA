import { useEffect, useState } from 'react'

export const COMPETENCIES: Record<number, string> = {
  1: 'Norma culta',
  2: 'Compreensão do tema',
  3: 'Argumentação',
  4: 'Coesão',
  5: 'Proposta de intervenção',
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export function trendLabel(trend: string): string {
  if (trend === 'up') return '▲ subindo'
  if (trend === 'down') return '▼ caindo'
  return '● estável'
}

// Carrega dados de uma função assíncrona e controla loading/erro.
export function useLoad<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    setError(null)
    load()
      .then((result) => active && setData(result))
      .catch((err: Error) => active && setError(err.message))
    return () => {
      active = false
    }
  }, [...deps, version])

  return { data, error, reload: () => setVersion((v) => v + 1) }
}
