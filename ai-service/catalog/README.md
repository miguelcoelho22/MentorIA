# Catálogo de atividades

Atividades que o plano de estudos pode recomendar, cobrindo as cinco provas do ENEM. É a **única** fonte de títulos e descrições mostrados ao aluno: o modelo só escolhe ids desta lista e as semanas em que cada atividade entra.

| Arquivo | Conteúdo |
|---|---|
| `redacao.md` | 41 atividades por competência (1 a 5) e nível, mais o simulado de redação |
| `matematica.md` | 10 atividades: operações, porcentagem, proporção, gráficos, estatística, probabilidade, geometria e funções |
| `natureza.md` | 13 atividades de Física, Química e Biologia |
| `humanas.md` | 10 atividades de História, Geografia, Filosofia e Sociologia |
| `linguagens.md` | 9 atividades de Português, Literatura, Inglês, Espanhol e Artes |
| `geral.md` | Simulado de área, caderno de erros e estratégia de prova (TRI) |

O catálogo é vetorizado no pgvector (tabela `activity_catalog`) ao iniciar o AI Service, antes da base de redações, de forma incremental.

## Formato

```markdown
---
id: fis-eletricidade
subject: fisica
difficulty: intermediario
minutes: 50
topics: eletricidade
title: Circuitos elétricos e consumo de energia
---
Descrição da atividade, escrita para o aluno.
```

- `subject`: disciplina (`portugues`, `literatura`, `ingles`, `espanhol`, `artes`, `historia`, `geografia`, `filosofia`, `sociologia`, `fisica`, `quimica`, `biologia`, `matematica`, `redacao` ou `geral`). A área do ENEM é deduzida da disciplina.
- `difficulty`: `basico`, `intermediario` ou `avancado`.
- `topics`: tópicos da disciplina, da lista em `src/ai_service/catalog.py`.
- Atividades de Redação usam `competency` (1 a 5, ou 0 para atividades gerais de redação) e `level` (40, 80, 120 ou 160, o nível atual do aluno para o qual a atividade é indicada) no lugar de `difficulty`.
- `minutes`: duração estimada; o plano respeita o total de horas por semana.
- `id`: único em todo o catálogo.

As 86 atividades foram escritas para o MVP a partir dos conteúdos mais cobrados no ENEM. Como elas definem o que o aluno vai estudar, vale um professor de cada área revisar antes de usar com alunos reais.
