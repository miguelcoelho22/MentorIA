# Corpus do RAG

Base de conhecimento usada pelo AI Service para corrigir redações.

| Arquivo | Uso |
|---|---|
| `rubrica.md` | Critérios das cinco competências. Vai **inteiro** no prompt de toda análise; não é vetorizado. |
| `essay-br.jsonl` | **1.501 redações reais** corrigidas por humanos, com as cinco notas. Cada redação vira um vetor; na análise entram as `RAG_ESSAY_K` mais parecidas com a do aluno, com suas notas. |
| `exemplos/*.md` | Trechos curtos por competência e nível, **sintéticos**, escritos para o MVP. Entram os `RAG_TOP_K` mais parecidos de cada competência. |

A rubrica foi escrita com base na estrutura pública da Matriz de Referência do ENEM.

## Redações reais: Essay-BR

As redações vêm do [Extended Essay-BR](https://github.com/lplnufpi/essay-br) (LPLN/UFPI, licença MIT; ver `ESSAY-BR-LICENSE.txt`): 6.577 redações de estudantes do ensino médio, coletadas do UOL e corrigidas por profissionais seguindo os critérios do ENEM.

O importador `scripts/import_essay_br.py` baixa uma versão fixa (commit `7f0ea9c`, com verificação de hash), descarta textos muito curtos ou longos e separa, de forma estratificada pela nota total e reproduzível:

- `corpus/essay-br.jsonl`: 1.501 redações para o RAG;
- `eval/essay-br-test.jsonl`: 200 redações que **nunca** entram no RAG, usadas por `./mentoria ai-eval`.

Para regenerar ou mudar os tamanhos: `./mentoria ai-import --corpus-size 3000 --eval-size 300`.

Formato de cada linha:

```json
{"id": "essay-br-123", "theme": "Tema", "scores": [120, 160, 120, 160, 80], "text": "..."}
```

Para acrescentar redações de outra fonte, crie outro `.jsonl` nesta pasta com o mesmo formato.

> Referência: MARINHO, J.; ANCHIÊTA, R.; MOURA, R. *Essay-BR: a Brazilian Corpus to Automatic Essay Scoring Task*. Journal of Information and Data Management, v. 13, n. 1, p. 65–76, 2022. DOI: 10.5753/jidm.2022.2340.

## Formato dos trechos (`exemplos/`)

Cada arquivo pode ter vários trechos. Cada trecho começa com um cabeçalho entre `---`:

```markdown
---
competency: 5
level: 160
theme: Tema da redação (opcional)
---
Trecho: "..."

Comentário do corretor: ...
```

- `competency`: 1 a 5.
- `level`: 0, 40, 80, 120, 160 ou 200.
- Trechos com mais de 1500 caracteres são divididos por parágrafo, mantendo os metadados.

## Ingestão

A ingestão é incremental: cada arquivo tem um hash, e só os arquivos novos ou alterados geram embeddings de novo. Arquivos apagados saem do banco.

- Automática ao iniciar o AI Service (`AUTO_INGEST=true`, padrão).
- Manual: `./mentoria ai-ingest`.
