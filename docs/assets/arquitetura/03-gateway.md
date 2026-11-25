
# API Gateway — Roteamento e Segurança

## 1. Decisão arquitetural

A MentorIA utilizará o **Spring Cloud Gateway** como ponto único de entrada das requisições externas.

O Gateway será responsável pelo roteamento, pela validação do JWT, pela autorização por papel, pelo rate limiting, pelo CORS e pela identificação das requisições.

A agregação de dados ficará nos BFFs, enquanto as regras de negócio permanecerão nos microsserviços de domínio.

## 2. Regras de roteamento

O Gateway encaminhará as requisições de acordo com o prefixo da URL:

| Rota | Destino | Autorização |
|---|---|---|
| `/api/v1/auth/**` | Account | Não exige JWT para o login. |
| `/api/v1/student/**` | BFF Aluno | Exige JWT válido com papel `STUDENT`. |
| `/api/v1/teacher/**` | BFF Professor | Exige JWT válido com papel `TEACHER`. |

O prefixo `/api/v1` identifica a primeira versão da API. O padrão `/**` representa os caminhos incluídos naquela rota.

Por exemplo, uma requisição para `/api/v1/student/home` será encaminhada ao BFF Aluno. Já uma requisição de login em `/api/v1/auth/login` será direcionada ao Account.

Nas rotas protegidas, um token ausente, inválido ou expirado recebe HTTP `401 Unauthorized`. Quando o token é válido, mas o usuário não possui o papel exigido pela rota, a resposta é HTTP `403 Forbidden`.

Na Parte 3, o **Ingress do Kubernetes** encaminhará todo o tráfego `/api` ao Gateway, que aplicará essas regras de roteamento. As demais rotas serão direcionadas aos clientes web.

## 3. Responsabilidades do Gateway

| Responsabilidade | Funcionamento |
|---|---|
| **Roteamento** | Encaminha cada requisição ao componente correspondente. |
| **Autenticação** | Valida a assinatura e a validade do JWT emitido pelo Account. |
| **Autorização** | Verifica se o papel do usuário permite acessar a rota solicitada. |
| **Rate limiting** | Limita a frequência de requisições para proteger o sistema. |
| **CORS** | Controla quais origens podem realizar requisições à API pelos navegadores. |
| **Rastreamento** | Gera o `X-Correlation-Id` para acompanhar a requisição entre os componentes. |

### Autenticação e autorização

O Account é responsável pelo login e pela emissão do JWT.

Nas requisições protegidas, o Gateway valida o token e verifica o papel do usuário. Após a validação, encaminha as informações necessárias aos componentes internos pelos cabeçalhos `X-User-Id` e `X-User-Role`.

Esses cabeçalhos devem ser definidos pelo Gateway com base no token validado, e não aceitos diretamente como informações confiáveis enviadas pelo cliente.

Dessa forma, um aluno com papel `STUDENT` não poderá acessar as rotas reservadas a professores, que exigem `TEACHER`.

## 4. Rate limiting e cota de correções

O **rate limiting** e a **cota de correções** possuem objetivos diferentes.

| Característica | Rate limiting | Cota de correções |
|---|---|---|
| Objetivo | Proteger o sistema contra excesso de requisições. | Controlar o direito de uso das correções. |
| Responsável | API Gateway | Account |
| Medida | Requisições por minuto | Correções por mês |
| Natureza | Proteção técnica | Regra de negócio |

O Gateway aplicará limites por usuário autenticado, com uma restrição mais rigorosa nas solicitações de correção, pois elas iniciam operações que utilizam IA.

**Limites definidos:**

| Tipo de requisição | Limite |
|---|---|
| Requisições autenticadas em geral | 60 por minuto por usuário |
| Solicitações de correção de redação | 2 por minuto por usuário |

Quando o limite técnico é ultrapassado, o Gateway responde com HTTP `429 Too Many Requests`.

A cota mensal, por outro lado, é gerenciada pelo Account. No MVP, cada aluno tem direito a **quatro correções por mês**. A reserva e o consumo dessa cota fazem parte da SAGA orquestrada pelo Assessment.

Assim, mesmo que um aluno esteja dentro do limite de requisições por minuto, uma nova correção será recusada se ele já tiver esgotado sua cota mensal: o pedido recebe inicialmente `202 Accepted`, mas a solicitação termina com o estado `REJECTED`.

## 5. Justificativa e alternativas descartadas

A utilização do API Gateway centraliza responsabilidades que seriam repetidas nos diferentes pontos de entrada da aplicação.

Sem ele, cada BFF ou serviço precisaria tratar separadamente questões como validação do JWT, controle de acesso, CORS e limitação de requisições externas.

Também foi descartada a ideia de concentrar regras de negócio no Gateway. O controle da cota de correções, por exemplo, permanece no Account, pois está relacionado ao direito de uso do aluno.

O Gateway atua como ponto de entrada e proteção, sem substituir os BFFs na organização das respostas nem os microsserviços na execução das regras de negócio.

## 6. Implementação

O módulo [`gateway/`](../../../gateway) implementa as decisões acima com **Spring Cloud Gateway (WebFlux)** e **Spring Security Resource Server**.

| Arquivo | O que faz |
|---|---|
| `src/main/resources/application.yml` | Rotas `/api/v1/auth/**` → Account, `/api/v1/student/**` → BFF Aluno, `/api/v1/teacher/**` → BFF Professor (o caminho é repassado sem alteração) e os filtros `RequestRateLimiter`. |
| `security/SecurityConfig.java` | Valida o JWT RS256 com a chave pública do Account (`/api/v1/auth/jwks`) e o emissor `account`, converte a claim `role` em `ROLE_STUDENT`/`ROLE_TEACHER`, autoriza por rota e aplica o CORS. Qualquer rota fora da tabela é negada, então os serviços internos e os comandos da SAGA (`/quota-reservations`, `PUT /feedbacks`) não ficam acessíveis de fora. |
| `security/IdentityHeadersFilter.java` | Remove `X-User-Id` e `X-User-Role` enviados pelo cliente e os recria a partir das claims `sub` e `role` do token validado. |
| `tracing/CorrelationIdWebFilter.java` | Gera o `X-Correlation-Id` (descartando o do cliente), repassa aos BFFs e devolve na resposta, inclusive em `401`, `403` e `429`. |
| `ratelimit/InMemoryRateLimiter.java` | *Token bucket* por usuário (`sub` do JWT) usado pelo filtro `RequestRateLimiter`: 60 req/min no geral e 2 req/min em `POST /api/v1/student/essays/{id}/correction-requests`. |

**Variáveis de ambiente:** `ACCOUNT_SERVICE_URL`, `BFF_STUDENT_URL`, `BFF_TEACHER_URL` e `CORS_ALLOWED_ORIGINS` (lista separada por vírgula). Os limites ficam em `mentoria.gateway.rate-limit.*`.

**Rate limiting e réplicas:** o contador fica na memória do Gateway, o que é suficiente com uma réplica. Para rodar várias réplicas, basta trocar os beans `generalRateLimiter` e `correctionRateLimiter` pelo `RedisRateLimiter` do Spring Cloud Gateway e adicionar um Redis.

**Clientes web:** os apps chamam a API por caminho relativo (`/api/v1/...`, ver `clients/*/src/api.ts`). No Kubernetes o Ingress envia `/api` ao Gateway; no Docker Compose o nginx de cada cliente faz o mesmo repasse; no `npm run dev` o proxy do Vite aponta para `http://localhost:8080`. Assim cliente e API ficam na mesma origem e o CORS só é necessário em acessos diretos à porta do Gateway.

**Health checks:** `/actuator/health/readiness` e `/actuator/health/liveness` (usados pelas probes do Kubernetes). Nenhum outro endpoint do Actuator é exposto.
