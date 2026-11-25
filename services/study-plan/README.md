# Study Plan

Os comandos escrevem em `plan_write` e gravam um evento interno na mesma transação. O projetor consulta os eventos pendentes a cada 250 ms e materializa o plano e o progresso em `plan_read`. Os `GET` consultam as projeções. O seed usa os UUIDs de Ana e Bruno do Account. Para desativá-lo, defina `STUDY_PLAN_SEED_ENABLED=false`.

## Integração com Feedback

A arquitetura define o evento `FeedbackPublished`, mas deixa a topologia e o JSON sem especificação. O consumidor usa os seguintes valores padrão, configuráveis pelas propriedades indicadas.

| Item | Padrão | Propriedade |
| --- | --- | --- |
| Exchange topic durável | `mentoria.events` | `study-plan.rabbitmq.exchange` |
| Fila durável | `study-plan.feedback-published` | `study-plan.rabbitmq.queue` |
| Routing key | `feedback.published` | `study-plan.rabbitmq.routing-key` |

Publique o JSON diretamente no corpo da mensagem (`application/json`). `eventId` identifica a publicação e deve ser reutilizado nas reentregas. `requestId` identifica a correção. Envie as cinco competências distintas, numeradas de 1 a 5, com notas inteiras de 0 a 200. `publishedAt` e `totalScore` são opcionais. Sem horário, vale o momento do processamento. Quando informado, o total deve igualar a soma das notas.

```json
{
  "eventId": "fdc82760-b793-4be3-88bc-cbc80ecf8963",
  "requestId": "9ec7ccff-52e8-4ac0-9e1b-a18b90249d9e",
  "studentId": "4155387b-4518-4657-bc51-d224aa1a69cb",
  "publishedAt": "2026-10-20T14:02:41Z",
  "totalScore": 640,
  "competencies": [
    {"id": 1, "score": 160},
    {"id": 2, "score": 160},
    {"id": 3, "score": 140},
    {"id": 4, "score": 100},
    {"id": 5, "score": 80}
  ]
}
```

Cada correção recomenda uma tarefa para a competência com a menor nota. Em caso de empate, usa a competência de menor ID. Uma tarefa pendente da mesma competência é atualizada. A projeção calcula a tendência comparando as duas últimas notas e a competência mais fraca pela menor média do histórico.
