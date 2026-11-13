# MentorIA - Infraestrutura Docker Compose

Orquestração local contendo **16 containers** (10 aplicações, 5 bancos de dados e 1 mensageria).

## Isolamento de Redes

Adotamos o padrão de menor privilégio separando as redes em três níveis:

- **`edge`**: Acesso público (Bridge). Conecta os clientes React (`client-student`, `client-teacher`) ao `gateway`.
- **`app`**: Rede interna de comunicação entre os microsserviços, BFFs, Gateway e RabbitMQ.
- **Redes Privadas de Banco (`internal: true`)**: Cada serviço possui uma rede exclusiva com seu respectivo banco PostgreSQL (`account-db`, `assessment-db`, `feedback-db`, `study-plan-db`, `ai-db`), impedindo acesso cruzado.

## Persistência de Dados

Para suportar o _Database per Service_, os volumes nomeados garantem que os dados sobrevivam à recriação dos containers:

- `account_data`, `assessment_data`, `feedback_data`, `study_plan_data`, `ai_data` e `rabbitmq_data`.

## Comandos de Operação

Subir a infraestrutura em background:
`docker compose up -d`

Derrubar a infraestrutura (mantendo os dados dos volumes):
`docker compose down`

Derrubar e limpar o banco de dados (Wipe):
`docker compose down -v`
