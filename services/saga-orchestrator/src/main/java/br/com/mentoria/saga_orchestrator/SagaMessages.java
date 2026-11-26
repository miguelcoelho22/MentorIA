package br.com.mentoria.saga_orchestrator;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Contratos das mensagens trocadas pela SAGA de correção de redação. */
final class SagaMessages {

    private SagaMessages() { }

    /** Assessment → orquestrador: o aluno pediu a correção (outbox do Assessment). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SagaStarted(UUID requestId, UUID essayId, UUID studentId) { }

    /** Orquestrador → Account: reservar uma unidade da cota mensal. */
    record ValidateQuota(Long sagaId, UUID requestId, UUID studentId) { }

    /** Account → orquestrador: resultado da reserva (outbox do Account). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuotaValidated(Long sagaId, UUID requestId, UUID studentId, boolean aprovada, String periodo, String motivo) { }

    /** Orquestrador → AI Service: corrigir a redação com RAG. */
    record AnalyzeEssay(Long sagaId, UUID requestId, UUID studentId, UUID essayId, String theme, String text) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Competency(Integer id, Integer score, String comment) { }

    /** AI Service → orquestrador: status OK com as notas ou FAILED com o motivo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EssayAnalyzed(Long sagaId, UUID requestId, String status, List<Competency> competencies, String error) { }

    /** Orquestrador → Feedback: gravar o rascunho e publicar o feedback. */
    record GenerateFeedback(Long sagaId, UUID requestId, UUID studentId, UUID essayId, List<Competency> competencies) { }

    /** Feedback → orquestrador: não foi possível gravar ou publicar o feedback. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedbackFailed(Long sagaId, UUID requestId, String error) { }

    /** Feedback → todos (outbox do Feedback): feedback publicado. É o ponto pivô da SAGA. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedbackPublished(Long sagaId, UUID requestId, Integer totalScore) { }

    /** Orquestrador → Assessment: estado final da solicitação de correção. */
    record CorrectionResult(UUID requestId, String status, String reason) { }
}
