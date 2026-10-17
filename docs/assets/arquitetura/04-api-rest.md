# API REST — Contratos dos Serviços

## 1. Decisão

Os quatro serviços de domínio expõem APIs REST em JSON, versionadas pelo prefixo `/api/v1` no caminho, com erros no formato Problem Details e HATEOAS no recurso de solicitação de correção. Um único identificador, o `requestId`, rastreia a correção nos três serviços da SAGA.

---

## 2. Como funciona

### 2.1 Convenções

| Regra | Exemplo |
|---|---|
| Recursos são substantivos no plural, em *kebab-case*, nunca verbos. | `/correction-requests`, e não `/requestCorrection` |
| Sub-recursos indicam pertencimento. | `/essays/{essayId}/correction-requests` |
| Filtros usam *query string*. | `/essays?studentId=b7e1` |
| Campos JSON em *camelCase*; datas em ISO 8601, UTC. | `"requestedAt": "2026-10-20T14:02:11Z"` |
| Identificadores são UUID em texto. Os exemplos usam valores curtos. | `"essayId": "41ab"` |
| Erros em Problem Details (`application/problem+json`). | Ver abaixo |
| Links HATEOAS no campo `_links`. | Ver seção 2.4 |

Todos os caminhos deste documento têm o prefixo `/api/v1`, omitido nas tabelas para facilitar a leitura. Por exemplo, `GET /themes` é `GET /api/v1/themes`.

Os cabeçalhos `X-User-Id`, `X-User-Role` e `X-Correlation-Id` são gerados pelo gateway a partir do JWT validado e repassados pelos BFFs aos serviços.

**Verbos HTTP:**

| Verbo | Uso | Idempotente? |
|---|---|---|
| `GET` | Consultar, sem efeito colateral. | Sim |
| `POST` | Criar um recurso cujo identificador é gerado pelo servidor (redação, solicitação de correção). | Não |
| `PUT` | Criar ou confirmar um recurso cujo identificador já é conhecido por quem chama (comandos da SAGA, identificados pelo `requestId`). | Sim |
| `PATCH` | Alterar parte de um recurso (concluir uma tarefa). | — |
| `DELETE` | Remover ou desfazer (compensações da SAGA). | Sim |

**Códigos de status:**

| Código | Significado | Exemplo na MentorIA |
|---|---|---|
| `200 OK` | Requisição atendida. | Consulta de uma redação. |
| `201 Created` | Recurso criado; `Location` aponta para ele. | Envio de redação; reserva de cota criada. |
| `202 Accepted` | Pedido aceito, processado depois da resposta. | Solicitação de correção: a SAGA continua em segundo plano. |
| `204 No Content` | Sucesso sem corpo de resposta. | Publicar o feedback; liberar a reserva. |
| `400 Bad Request` | Corpo ou parâmetros malformados. Vale para qualquer rota. | Redação sem texto. |
| `401 Unauthorized` | Credenciais inválidas, ou token ausente/expirado (este último respondido pelo gateway). | Login com senha errada. |
| `403 Forbidden` | Autenticado, mas sem permissão (respondido pelo gateway ou BFF). | Aluno acessando rota de professor. |
| `404 Not Found` | Recurso inexistente ou não visível. | Feedback ainda em `DRAFT`. |
| `409 Conflict` | Conflito com o estado atual do recurso. | Cota esgotada; cancelar um feedback já publicado. |
| `429 Too Many Requests` | Limite de requisições excedido (respondido pelo gateway). | Mais pedidos de correção do que o permitido por minuto. |
| `503 Service Unavailable` | Dependência indisponível. | AI Service fora do ar. |

**Formato de erro (Problem Details):**

```json
{
  "type": "https://mentoria.app/errors/quota-exhausted",
  "title": "Cota de correções esgotada",
  "status": 409,
  "detail": "O aluno já usou as 4 correções de outubro de 2026.",
  "instance": "/api/v1/quota-reservations/9f2c"
}
```

### 2.2 Versionamento

| Mudança | Efeito na versão |
|---|---|
| Novo campo opcional, novo endpoint, novo parâmetro opcional | Continua `/api/v1`. Os consumidores ignoram campos que não conhecem. |
| Remover ou renomear campo, mudar tipo, mudar o significado de um status | Nova versão `/api/v2`, que convive com a `/api/v1` durante a transição. |

O gateway e o Ingress encaminham cada versão apenas pelo prefixo do caminho.

### 2.3 Endpoints por serviço

#### Account

| Verbo | Caminho | Sucesso | Erros | Quem chama |
|---|---|---|---|---|
| `POST` | `/auth/login` | 200 com o token | 401 | Gateway (rota pública) |
| `GET` | `/students/{studentId}` | 200 | 404 | BFFs |
| `GET` | `/classes?teacherId=` | 200 | | BFF Professor |
| `GET` | `/classes/{classId}/students` | 200 | 404 | BFF Professor |
| `GET` | `/students/{studentId}/quota` | 200 com limite, reservadas e consumidas | 404 | BFF Aluno |
| `PUT` | `/quota-reservations/{requestId}` | 201 criada, 200 se já existia | 409 cota esgotada | Assessment (SAGA) |
| `PUT` | `/quota-reservations/{requestId}/confirmation` | 204 | 404 | Assessment (SAGA) |
| `DELETE` | `/quota-reservations/{requestId}` | 204, mesmo se não existir | 409 se já consumida | Assessment (SAGA) |

Estados da reserva: `RESERVED` → `CONSUMED` (confirmação) ou `RESERVED` → `RELEASED` (compensação). `RESERVED` e `CONSUMED` contam contra o limite de 4 correções por mês.

**Exemplo — login:**

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "ana@escola.edu.br", "password": "********" }
```

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": "b7e1", "name": "Ana Souza", "role": "STUDENT" }
}
```

**Exemplo — cota do aluno:**

```json
{ "studentId": "b7e1", "period": "2026-10", "limit": 4, "reserved": 1, "consumed": 2, "available": 1 }
```

**Exemplo — reserva de cota (passo 2 da SAGA):**

```http
PUT /api/v1/quota-reservations/9f2c
Content-Type: application/json

{ "studentId": "b7e1" }
```

```http
HTTP/1.1 201 Created
Location: /api/v1/quota-reservations/9f2c

{ "requestId": "9f2c", "studentId": "b7e1", "status": "RESERVED", "period": "2026-10" }
```

#### Assessment

| Verbo | Caminho | Sucesso | Erros | Quem chama |
|---|---|---|---|---|
| `GET` | `/themes` | 200 | | BFF Aluno |
| `POST` | `/essays` | 201 com `Location` | 400 | BFF Aluno |
| `GET` | `/essays/{essayId}` | 200 | 404 | BFFs |
| `GET` | `/essays?studentId=` | 200 | | BFFs |
| `POST` | `/essays/{essayId}/correction-requests` | 202 com `Location` | 404, 409 se já há uma em andamento | BFF Aluno |
| `GET` | `/correction-requests/{requestId}` | 200 com `_links` | 404 | BFF Aluno |

**Exemplo — envio de redação:**

```http
POST /api/v1/essays
X-User-Id: b7e1
Content-Type: application/json

{ "themeId": "t07", "text": "Segundo a Constituição Federal de 1988, o transporte é um direito social..." }
```

```http
HTTP/1.1 201 Created
Location: /api/v1/essays/41ab

{ "id": "41ab", "studentId": "b7e1", "themeId": "t07", "submittedAt": "2026-10-20T14:01:50Z" }
```

**Exemplo — solicitação de correção:**

```http
POST /api/v1/essays/41ab/correction-requests
X-User-Id: b7e1
```

```http
HTTP/1.1 202 Accepted
Location: /api/v1/correction-requests/9f2c

{ "id": "9f2c", "essayId": "41ab", "status": "PROCESSING" }
```

#### Feedback

| Verbo | Caminho | Sucesso | Erros | Quem chama |
|---|---|---|---|---|
| `PUT` | `/feedbacks/{requestId}` | 201 rascunho criado, 200 se já existia | 503 IA indisponível | Assessment (SAGA) |
| `PUT` | `/feedbacks/{requestId}/publication` | 204 | 404, 409 se cancelado | Assessment (SAGA) |
| `DELETE` | `/feedbacks/{requestId}` | 204, mesmo se não existir | 409 se já publicado | Assessment (SAGA) |
| `GET` | `/feedbacks/{requestId}` | 200, só se publicado | 404 | BFFs |
| `GET` | `/feedbacks?studentId=` | 200 | | BFF Professor |

Estados do feedback: `DRAFT`, `PUBLISHED` e `CANCELLED`. Só `PUBLISHED` é visível ao aluno; por isso `GET /feedbacks/{requestId}` responde 404 para um rascunho.

**Exemplo — criação do rascunho (passo 3 da SAGA).** O orquestrador envia o texto da redação no corpo, para que o Feedback não precise chamar o Assessment de volta:

```http
PUT /api/v1/feedbacks/9f2c
Content-Type: application/json

{
  "studentId": "b7e1",
  "essayId": "41ab",
  "theme": "Desafios da mobilidade urbana no Brasil",
  "text": "Segundo a Constituição Federal de 1988, o transporte é um direito social..."
}
```

```http
HTTP/1.1 201 Created
Location: /api/v1/feedbacks/9f2c

{
  "requestId": "9f2c",
  "status": "DRAFT",
  "totalScore": 640,
  "competencies": [
    { "id": 1, "score": 160, "comment": "Poucos desvios; atenção à regência verbal." },
    { "id": 2, "score": 120, "comment": "Tema compreendido, repertório pouco explorado." },
    { "id": 3, "score": 120, "comment": "Argumentos pouco desenvolvidos." },
    { "id": 4, "score": 160, "comment": "Boa articulação entre parágrafos." },
    { "id": 5, "score": 80,  "comment": "Faltam agente e detalhamento da ação." }
  ]
}
```

#### Study Plan

| Verbo | Caminho | Sucesso | Erros | Quem chama |
|---|---|---|---|---|
| `GET` | `/students/{studentId}/plan` | 200, modelo de leitura | 404 | BFF Aluno |
| `GET` | `/students/{studentId}/progress` | 200, modelo de leitura | 404 | BFFs |
| `GET` | `/progress?studentIds=` | 200, resumo de vários alunos | | BFF Professor |
| `PATCH` | `/tasks/{taskId}` | 200, tarefa concluída | 404, 409 se já concluída | BFF Aluno |

Os três `GET` leem o modelo de leitura do CQRS (`07-cqrs.md`), com defasagem de até 2 s. As tarefas não têm rota de criação: o Study Plan as cria ao consumir o evento `FeedbackPublished`. Estados da tarefa: `PENDING` e `DONE`.

**Exemplo — concluir tarefa:**

```http
PATCH /api/v1/tasks/t-18
Content-Type: application/json

{ "status": "DONE" }
```

```json
{ "id": "t-18", "title": "Proposta de intervenção: 2 exercícios", "competency": 5, "status": "DONE" }
```

#### Comandos da SAGA

Os três `PUT` e os dois `DELETE` de reserva e de feedback são os comandos da SAGA. Só o Assessment os chama, e nenhum BFF os expõe.

| Passo da SAGA (`08-SAGA.md`) | Comando |
|---|---|
| 2. Reservar a cota | `PUT /quota-reservations/{requestId}` |
| 3. Criar o rascunho | `PUT /feedbacks/{requestId}` |
| 4. Publicar (pivô) | `PUT /feedbacks/{requestId}/publication` |
| 5. Confirmar o consumo | `PUT /quota-reservations/{requestId}/confirmation` |
| Compensação: cancelar o rascunho | `DELETE /feedbacks/{requestId}` |
| Compensação: liberar a reserva | `DELETE /quota-reservations/{requestId}` |

### 2.4 HATEOAS na solicitação de correção

A solicitação de correção tem quatro estados, e os links oferecidos mudam com eles:

| Estado | Significado | Links oferecidos |
|---|---|---|
| `PROCESSING` | Saga em andamento | `self`, `essay` |
| `COMPLETED` | Feedback publicado | `self`, `essay`, `feedback`, `study-plan` |
| `REJECTED` | Cota esgotada | `self`, `essay`, `quota` |
| `FAILED` | Falha técnica, já compensada | `self`, `essay`, `retry` |

O link `retry` aponta para a criação de uma nova solicitação (`POST`) na mesma redação.

O Assessment gera os links com os caminhos internos. O BFF Aluno reescreve cada `href` para a rota que ele expõe ao app:

| Link | Caminho interno (Assessment) | Caminho entregue ao app (BFF Aluno) |
|---|---|---|
| `self` | `/api/v1/correction-requests/9f2c` | `/api/v1/student/correction-requests/9f2c` |
| `essay` | `/api/v1/essays/41ab` | `/api/v1/student/essays/41ab` |
| `feedback` | `/api/v1/feedbacks/9f2c` | `/api/v1/student/feedbacks/9f2c` |
| `study-plan` | `/api/v1/students/b7e1/plan` | `/api/v1/student/plan` |
| `quota` | `/api/v1/students/b7e1/quota` | `/api/v1/student/quota` |
| `retry` | `/api/v1/essays/41ab/correction-requests` | `/api/v1/student/essays/41ab/correction-requests` |

**Resposta interna do Assessment, em `COMPLETED`:**

```json
{
  "id": "9f2c",
  "essayId": "41ab",
  "status": "COMPLETED",
  "requestedAt": "2026-10-20T14:02:11Z",
  "_links": {
    "self":       { "href": "/api/v1/correction-requests/9f2c" },
    "essay":      { "href": "/api/v1/essays/41ab" },
    "feedback":   { "href": "/api/v1/feedbacks/9f2c" },
    "study-plan": { "href": "/api/v1/students/b7e1/plan" }
  }
}
```

**Como o app do aluno recebe cada estado:**

`PROCESSING`

```json
{
  "id": "9f2c",
  "essayId": "41ab",
  "status": "PROCESSING",
  "requestedAt": "2026-10-20T14:02:11Z",
  "_links": {
    "self":  { "href": "/api/v1/student/correction-requests/9f2c" },
    "essay": { "href": "/api/v1/student/essays/41ab" }
  }
}
```

`COMPLETED`

```json
{
  "id": "9f2c",
  "essayId": "41ab",
  "status": "COMPLETED",
  "requestedAt": "2026-10-20T14:02:11Z",
  "_links": {
    "self":       { "href": "/api/v1/student/correction-requests/9f2c" },
    "essay":      { "href": "/api/v1/student/essays/41ab" },
    "feedback":   { "href": "/api/v1/student/feedbacks/9f2c" },
    "study-plan": { "href": "/api/v1/student/plan" }
  }
}
```

`REJECTED`

```json
{
  "id": "9f2c",
  "essayId": "41ab",
  "status": "REJECTED",
  "requestedAt": "2026-10-20T14:02:11Z",
  "reason": "QUOTA_EXHAUSTED",
  "_links": {
    "self":  { "href": "/api/v1/student/correction-requests/9f2c" },
    "essay": { "href": "/api/v1/student/essays/41ab" },
    "quota": { "href": "/api/v1/student/quota" }
  }
}
```

`FAILED`

```json
{
  "id": "9f2c",
  "essayId": "41ab",
  "status": "FAILED",
  "requestedAt": "2026-10-20T14:02:11Z",
  "reason": "AI_UNAVAILABLE",
  "_links": {
    "self":  { "href": "/api/v1/student/correction-requests/9f2c" },
    "essay": { "href": "/api/v1/student/essays/41ab" },
    "retry": { "href": "/api/v1/student/essays/41ab/correction-requests" }
  }
}
```

### 2.5 Contratos OpenAPI

O contrato completo de cada serviço, com os esquemas e exemplos de corpo, está na pasta `openapi/`: `account.yaml`, `assessment.yaml`, `feedback.yaml` e `study-plan.yaml`.

---

## 3. Por que essas escolhas

**Versão no caminho.** O prefixo `/api/v1` aparece nos logs, pode ser testado com `curl` sem configurar cabeçalhos e permite que o gateway e o Ingress roteiem as versões só pelo caminho. A alternativa, versão por cabeçalho (`Accept: application/vnd.mentoria.v1+json`), foi descartada por dificultar o roteamento e o teste manual.

**`PUT` e `DELETE` nos comandos da SAGA, em vez de `POST`.** O orquestrador já conhece o identificador do recurso, que é o `requestId`, então ele pode endereçá-lo diretamente. `PUT` e `DELETE` são idempotentes por definição do HTTP: repetir o comando depois de um *timeout* não reserva a cota duas vezes nem cria dois rascunhos. Com `POST`, cada repetição poderia criar um recurso novo, e seria preciso um mecanismo extra de deduplicação.

**Um único `requestId`.** O mesmo UUID identifica a solicitação, a saga, a reserva e o feedback. Basta um valor para rastrear a transação nos três serviços, e o orquestrador nunca precisa guardar identificadores devolvidos pelos participantes.

**`DELETE` responde 204 mesmo quando o recurso não existe.** Depois de um *timeout*, o orquestrador não sabe se o passo aconteceu. Como a compensação é segura nos dois casos, ele a envia sempre, sem consulta prévia.

**`202 Accepted` no pedido de correção.** A correção depende do LLM e leva segundos. Responder `201` exigiria segurar a conexão até o fim da SAGA. Com `202`, o app recebe na hora o endereço da solicitação e acompanha o estado por ele.

**HATEOAS na solicitação de correção.** É o recurso com uma máquina de estados visível ao usuário. Como os links mudam com o estado, o app não precisa conhecer as regras: mostra "tentar de novo" só se recebeu `retry`, e "ver feedback" só se recebeu `feedback`. A alternativa, o app decidir as ações pelo campo `status`, espalharia a regra de negócio pelo cliente.

**Problem Details nos erros.** É um formato padronizado (RFC 9457) já oferecido pelo Spring, e permite que os BFFs tratem os erros de todos os serviços da mesma forma. A alternativa, cada serviço com seu próprio formato, obrigaria os BFFs a interpretar quatro formatos diferentes.
