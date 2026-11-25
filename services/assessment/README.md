# Assessment

O seed inclui três temas e uma redação de demonstração para cada aluno do Account, Ana e Bruno. Para cadastrar uma redação ou abrir uma correção, envie o UUID do aluno em `X-User-Id`. A listagem aceita `studentId`, `classId` ou ambos. O filtro por turma usa os alunos que o Account retorna e, quando os dois filtros são enviados, aplica a interseção. O texto integral fica na rota de detalhe.

## RabbitMQ

A abertura de uma correção retorna `202` com `Location`. O Assessment grava a solicitação e o evento de início na mesma transação. O publicador marca o evento como enviado depois que o RabbitMQ confirma a publicação e o roteamento. Reenvios preservam o `message_id`, que contém o UUID da solicitação.

O início usa a exchange direta `mentoria.saga`, a routing key `redacao.enviada` e a fila `saga.redacao.enviada`. O corpo JSON contém somente `redacaoId` e `alunoId`, conforme o `RedacaoEnviadaEvent` do orquestrador.

O orquestrador atual encaminha `redacaoId` como `requestId` ao Account, e seu listener tipado ainda precisa de conversão do JSON. Para correlacionar tentativas da mesma redação, o orquestrador precisa usar o `message_id` como `requestId` nos comandos seguintes. O código atual do orquestrador não publica o resultado final. O Assessment recebe esse resultado na fila e routing key `assessment.correcao.resultado` da mesma exchange, em JSON. Exemplo:

```json
{"requestId":"b6f39305-960b-42bc-9d2c-bcb5ee94b02f","status":"FAILED","reason":"AI_UNAVAILABLE"}
```

São aceitos `COMPLETED`, `REJECTED` e `FAILED`. O resultado atualiza somente a solicitação indicada e deixa um estado terminal intacto em caso de reentrega. O orquestrador deve emitir esse resultado depois de concluir ou compensar os serviços participantes.

## Compose local

O Compose principal ainda não injeta as credenciais RabbitMQ no Assessment. O arquivo complementar reutiliza as credenciais do mesmo `.env`, sem gravar senhas na imagem. Com PostgreSQL, Account e RabbitMQ em execução:

```powershell
docker compose -f infrastructure/docker/docker-compose.yaml -f services/assessment/compose.rabbitmq.yaml up -d --build --no-deps assessment
```
