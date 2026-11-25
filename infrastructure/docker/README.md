# MentorIA - Infraestrutura Docker Compose

Orquestração local contendo **16 containers** (10 aplicações, 5 bancos de dados e 1 mensageria).

## Isolamento de Redes

Adotamos o padrão de menor privilégio separando as redes em três níveis:

- **`edge`**: Acesso público (Bridge). Conecta os clientes React (`client-student`, `client-teacher`) ao `gateway`.
- **`app`**: Rede interna de comunicação entre os microsserviços, BFFs, Gateway e RabbitMQ.
- **Redes Privadas de Banco (`internal: true`)**: Cada serviço possui uma rede exclusiva com seu respectivo banco PostgreSQL (`account-db`, `assessment-db`, `feedback-db`, `study-plan-db`, `ai-db`), impedindo acesso cruzado.

## Ponto de Entrada (API Gateway)

Toda requisição externa à API passa pelo `gateway` (porta `8080`), que valida o JWT emitido pelo Account, autoriza por papel, aplica rate limiting e repassa `X-User-Id`, `X-User-Role` e `X-Correlation-Id` aos BFFs. Os clientes (`3000` aluno, `3001` professor) chamam `/api/v1/...` na própria origem e o nginx de cada um encaminha ao gateway. Detalhes em [03 — API Gateway](../../docs/assets/arquitetura/03-gateway.md#6-implementação).

Exemplo de login pelo gateway:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@demo.mentoria.test","password":"MentorIA-demo-2026!"}'
```

## Persistência de Dados

Para suportar o _Database per Service_, os volumes nomeados garantem que os dados sobrevivam à recriação dos containers:

- `account_data`, `assessment_data`, `feedback_data`, `study_plan_data`, `ai_data` e `rabbitmq_data`.

## Credenciais

Nenhuma senha fica no código. O compose lê usuários e senhas do arquivo `.env`
desta pasta, que está no `.gitignore`. Antes de subir pela primeira vez:

```bash
cp .env.example .env
# edite o .env e preencha as senhas
```

Se alguma variável obrigatória faltar, o `docker compose` para com a mensagem
`defina <VARIÁVEL> no .env`.

## Comandos de Operação

Subir a infraestrutura em background:
`docker compose up -d`

Derrubar a infraestrutura (mantendo os dados dos volumes):
`docker compose down`

Derrubar e limpar o banco de dados (Wipe):
`docker compose down -v`
