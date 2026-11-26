from typing import Literal
from uuid import UUID

from pydantic import BaseModel

from ai_service.analysis import CompetencyScore


class ProcessarRedacaoCommand(BaseModel):
    sagaId: int
    requestId: UUID
    studentId: UUID
    essayId: UUID
    theme: str
    text: str


class RedacaoAnalisadaEvent(BaseModel):
    """Resposta do AI Service à SAGA: sucesso com as notas ou falha com o motivo."""
    sagaId: int
    requestId: UUID
    studentId: UUID
    essayId: UUID
    status: Literal["OK", "FAILED"] = "OK"
    competencies: list[CompetencyScore] = []
    error: str | None = None
