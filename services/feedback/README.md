# Feedback

As consultas REST retornam os feedbacks publicados. O Assessment cria o rascunho com `PUT /api/v1/feedbacks/{requestId}` e usa o mesmo identificador para repetir o comando, cancelar ou publicar.

## Análise da redação

O cliente envia `POST /api/v1/analysis` ao endereço definido em `AI_SERVICE_URL`, com `studentId`, `essayId`, `theme` e `text` em JSON. A operação de análise ainda não está definida no contrato do AI Service. O caminho escolhido pode ser alterado por `feedback.ai.analysis-path`. A resposta esperada é `{"competencies":[...]}`, com cinco objetos `{ "id": 1, "score": 160, "comment": "..." }`, IDs distintos de 1 a 5 e notas de 0 a 200. O Feedback soma as notas para obter `totalScore`. Após as tentativas configuradas, uma análise ausente ou inválida retorna `503`.

## Outbox

Publicar um rascunho grava `FeedbackPublished` em `outbox_events` na mesma transação que atualiza o estado do feedback. O worker envia mensagens persistentes em JSON para a exchange topic `mentoria.events`, com a routing key `feedback.published`, e marca o evento como publicado após confirmação do RabbitMQ e desde que a mensagem não tenha sido devolvida pelo broker. Reenvios preservam o mesmo `eventId` para a deduplicação do Study Plan.
