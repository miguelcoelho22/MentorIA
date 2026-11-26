# Integração de LLM — RAG, LangChain e resiliência

O LLM tem um propósito de domínio: **corrigir redações do ENEM** nas cinco competências, com nota e comentário para o aluno, dentro da SAGA de correção. Ele também monta **planos de estudo** a partir de um catálogo fechado de atividades. O código fica em [`ai-service/`](../../../ai-service/README.md).

## RAG: pipeline completo

| Etapa | Onde | O que faz |
|---|---|---|
| Base de conhecimento | `ai-service/corpus/`, `ai-service/catalog/` | 1.501 redações reais corrigidas do Essay-BR (com as cinco notas), trechos de referência por competência e nível, a rubrica oficial do ENEM e o catálogo de atividades por área. |
| Ingestão | `ingest.py` | Roda sozinha quando o serviço sobe; é incremental (hash por trecho), então só vetoriza o que mudou. |
| Embeddings | `providers.py` | `OpenAIEmbeddings` do LangChain (OpenAI `text-embedding-3-small` ou Gemini `gemini-embedding-001`), 1.536 dimensões. |
| Base vetorial | `store.py`, `postgres-ai` (pgvector) | Tabela `corpus_chunks` com `embedding vector(1536)` e índice HNSW por distância de cosseno. |
| Recuperação | `Analyzer.retrieve` | Para a redação do aluno: as 3 redações corrigidas mais parecidas e os 3 trechos mais próximos de cada competência. |
| Geração | `Analyzer.chain` | Rubrica + exemplos recuperados + histórico do aluno + redação vão para o LLM, com saída estruturada (cinco notas múltiplas de 40 e um comentário por competência). |

Se a busca vetorial falhar, a correção continua só com a rubrica (degradação controlada, registrada em log).

## LangChain: chain e tool

A correção é uma chain LCEL (`analysis.py`):

```
RunnablePassthrough.assign(context = recuperação no pgvector)
  | RunnablePassthrough.assign(history = tool consultar_historico_redacoes)
  | montagem das variáveis
  | ChatPromptTemplate (sistema + aluno)
  | LLM com saída estruturada (protegido pelo circuit breaker)
  | validação (cinco competências, notas arredondadas para os níveis do ENEM)
```

A **tool** `consultar_historico_redacoes` (`tools.py`) consulta o serviço **Feedback** (`GET /api/v1/feedbacks?studentId=`) e devolve as notas por competência das correções anteriores do aluno. O resultado entra no prompt e o modelo diz, em cada comentário, se o aluno melhorou ou piorou. Se o Feedback estiver fora do ar, a tool devolve "histórico indisponível" e a correção segue.

Não usamos agente: a ordem dos passos é sempre a mesma (recuperar, consultar o histórico, corrigir), então não há decisão dinâmica que justifique um agente.

## LLM como dependência remota falível

| Mecanismo | Implementação |
|---|---|
| **Timeout e retry com backoff** | `ChatOpenAI(timeout=LLM_TIMEOUT_SECONDS, max_retries=LLM_MAX_RETRIES)`: o cliente da OpenAI repete erros de rede, 429 e 5xx com backoff exponencial. A ingestão também espera e repete quando o provedor devolve 429. |
| **Circuit breaker** | `resilience.CircuitBreaker` envolve a chamada ao LLM. Depois de 3 falhas seguidas (`CIRCUIT_FAILURE_THRESHOLD`) o circuito abre por 30 s (`CIRCUIT_OPEN_SECONDS`) e as correções falham na hora, sem gastar tempo nem cota do provedor. Depois do prazo, uma chamada de teste decide se fecha ou reabre. Respostas fora do contrato não abrem o circuito (o erro é nosso, não do provedor). |
| **Cache por chave** | `resilience.TtlCache`: hash de tema + texto + com/sem RAG, até 256 itens por 1 h. A mesma redação enviada de novo não gera uma segunda chamada paga. |
| **Fallback** | Para a busca vetorial: sem pgvector, a correção usa só a rubrica. Para o modelo: a SAGA é o fallback de negócio. O AI Service responde `FAILED`, o orquestrador compensa (cancela o rascunho e devolve a cota) e o aluno recebe o link para tentar de novo. |

## Latência e custo

O AI Service mede cada correção e expõe os números em `GET /api/v1/metrics` (`./mentoria ai-metrics`):

- latência média, p50, p95 e máxima, separando acertos de cache;
- tokens de entrada e saída por chamada, lidos do `usage_metadata` que o provedor devolve;
- custo estimado por correção, por mil e por cem mil correções, com o preço por milhão de tokens configurado (`LLM_PRICE_INPUT_PER_MILLION` e `LLM_PRICE_OUTPUT_PER_MILLION`);
- estado do circuit breaker e taxa de acerto do cache.

Estimativa de ordem de grandeza: o prompt de correção montado pelo sistema (rubrica, três redações recuperadas, quinze trechos, histórico e a redação) tem cerca de 8 mil caracteres, ou seja, por volta de 2 mil tokens de entrada, e a resposta fica perto de 400 tokens. Com o `gpt-4o-mini` (US$ 0,15 por milhão de tokens de entrada e US$ 0,60 por milhão de saída) isso dá cerca de **US$ 0,0005 por correção**, US$ 0,55 a cada mil e US$ 55 a cada cem mil correções. Redações recuperadas maiores aumentam o prompt. Os valores medidos com o provedor real ficam em `/api/v1/metrics` e devem substituir esta estimativa na apresentação. No plano gratuito do Gemini o custo é zero, mas o limite de requisições por minuto vira o gargalo.

## Avaliação do RAG

`./mentoria ai-eval-report 5` corrige cinco redações do conjunto de teste (Essay-BR, fora da base vetorizada) com e sem recuperação e grava em `docs/avaliacao-rag.md` a tabela redação por redação (nota humana × IA com RAG × IA sem RAG) e as métricas (erro médio por competência, erro na nota total, acerto exato e até 40 pontos). `./mentoria ai-eval 20` roda a mesma comparação com mais redações e mostra só as métricas.
