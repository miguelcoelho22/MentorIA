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
    sagaId: int
    requestId: UUID
    studentId: UUID
    essayId: UUID
    competencies: list[CompetencyScore]