"""Tools do LangChain que consultam dados reais da MentorIA.

``consultar_historico_redacoes`` busca no serviço Feedback as correções já publicadas do aluno.
A chain de correção usa o resultado para comparar a redação nova com as anteriores e dizer ao
aluno se ele evoluiu em cada competência.
"""

import logging
from typing import Callable
from uuid import UUID

import httpx
from langchain_core.tools import BaseTool, tool

log = logging.getLogger(__name__)

MAX_HISTORY = 5


def summarize_history(feedbacks: list[dict]) -> str:
    published = [f for f in feedbacks if isinstance(f, dict) and f.get("competencies")]
    if not published:
        return "Primeira redação corrigida deste aluno: não há histórico para comparar."
    lines = []
    for number, feedback in enumerate(published[:MAX_HISTORY], start=1):
        scores = sorted(feedback["competencies"], key=lambda c: c.get("id", 0))
        by_competency = " | ".join(f"C{c.get('id')}={c.get('score')}" for c in scores)
        date = str(feedback.get("publishedAt") or "")[:10] or "data desconhecida"
        lines.append(f"{number}. {date}: total={feedback.get('totalScore')} ({by_competency})")
    return "Correções anteriores do aluno, da mais recente para a mais antiga:\n" + "\n".join(lines)


def history_tool(feedback_url: str | None, timeout_seconds: float = 3.0,
                 transport: httpx.BaseTransport | None = None) -> BaseTool:
    """Cria a tool. Sem ``feedback_url`` (ex.: testes), a tool informa que não há histórico."""

    @tool("consultar_historico_redacoes")
    def consultar_historico_redacoes(student_id: str) -> str:
        """Consulta no serviço Feedback as notas por competência das redações já corrigidas do aluno."""
        if not feedback_url:
            return "Histórico indisponível: o serviço Feedback não está configurado."
        try:
            UUID(student_id)
        except ValueError:
            return "Histórico indisponível: identificador de aluno inválido."
        try:
            with httpx.Client(base_url=feedback_url, timeout=timeout_seconds, transport=transport) as client:
                response = client.get("/api/v1/feedbacks", params={"studentId": student_id})
                response.raise_for_status()
                body = response.json()
            return summarize_history(body if isinstance(body, list) else [])
        except Exception as error:
            # O histórico enriquece o comentário, mas não pode derrubar a correção.
            log.warning("Não foi possível consultar o histórico do aluno %s: %s", student_id, error)
            return "Histórico indisponível no momento."

    return consultar_historico_redacoes


HistoryLookup = Callable[[str], str]
