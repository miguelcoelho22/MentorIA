
# Microsserviços — Responsabilidades e Fronteiras

## 1. Decisão arquitetural

A MentorIA será dividida em quatro microsserviços de domínio: **Account, Assessment, Feedback e Study Plan**.

Cada serviço terá responsabilidades bem definidas e sua própria instância PostgreSQL. A divisão considera que os dados e as regras de cada domínio evoluem por motivos diferentes.

## 2. Responsabilidades e dados

| Serviço | Responsabilidades | Dados próprios |
|---|---|---|
| **Account** | Gerencia alunos, professores, turmas, login com emissão do JWT e cotas de correção. | Usuários, turmas, cotas e reservas. |
| **Assessment** | Gerencia temas, redações enviadas e solicitações de correção. Orquestra a SAGA. | Temas, redações, solicitações e estado da SAGA. |
| **Feedback** | Solicita a análise ao AI Service, cria rascunhos e publica ou cancela feedbacks. | Feedbacks, notas por competência e registros de outbox. |
| **Study Plan** | Gerencia planos de estudo, tarefas, recomendações e progresso por competência. Utiliza CQRS. | Planos, tarefas, eventos processados e projeções de leitura. |

## 3. Justificativa das fronteiras

### Account

Reúne informações de identidade e direito de uso da plataforma. O login, os vínculos com turmas e as cotas seguem regras administrativas, diferentes das regras pedagógicas dos demais serviços.

O Account emite o JWT durante o login, enquanto a validação do token nas requisições externas fica sob responsabilidade do API Gateway.

### Assessment

É responsável pelo conteúdo produzido pelo aluno e pelo ciclo de vida das solicitações de correção.

Manter as redações separadas do processamento com IA permite que o estudante continue enviando e consultando seus textos mesmo quando o Feedback estiver indisponível.

O Assessment também concentra a orquestração da SAGA, coordenando as operações necessárias para concluir ou compensar uma correção.

### Feedback

A geração de feedback depende de chamadas a um modelo de linguagem remoto, que podem apresentar latência elevada ou falhas.

Separar esse processamento permite tratar sua indisponibilidade sem comprometer diretamente o armazenamento das redações. Também possibilita aumentar os recursos ou as réplicas do Feedback sem escalar os demais serviços.

### Study Plan

O Study Plan cuida do que o aluno deve estudar a seguir, com base nas recomendações e no avanço do estudante. Essas informações evoluem por regras diferentes das utilizadas na correção de redações.

Enquanto o Feedback produz a avaliação de uma redação, o Study Plan utiliza seus resultados para definir tarefas e recomendações. Essa separação também permite que, futuramente, o plano de estudos considere outras fontes, como simulados, sem depender exclusivamente do Feedback.

Além disso, uma falha no Study Plan não interrompe a correção, pois a atualização das recomendações ocorre de forma assíncrona, fora da SAGA.

Como o serviço registra tarefas e progresso, mas também realiza consultas frequentes e agregadas, utiliza CQRS para separar os modelos de escrita e leitura.

## 4. Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| **Incluir o Feedback no Assessment** | A lentidão ou indisponibilidade da IA poderia afetar o envio e a consulta de redações. |
| **Criar um microsserviço exclusivo para cotas** | A cota faz parte do direito de uso do aluno e pertence ao Account. Separá-la criaria um serviço pequeno, sem uma fronteira de negócio independente. |
| **Criar um microsserviço exclusivo para progresso** | No MVP, o progresso é uma projeção das informações do plano de estudos. Um serviço separado acrescentaria complexidade desnecessária. |

## 5. Independência dos bancos de dados

Cada microsserviço terá sua própria instância PostgreSQL, com armazenamento independente. Nenhum serviço acessa diretamente o banco de outro. Identificadores externos, como `studentId` e `essayId`, são armazenados como referências, sem chaves estrangeiras entre bancos.

As justificativas das transações locais, a modelagem dos dados, a consistência eventual e o padrão Outbox serão detalhados no documento `06-dados-outbox.md`.
