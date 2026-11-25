from dataclasses import dataclass
from pathlib import Path

from ai_service.corpus import _ENTRY, _parse_meta

AREAS = {
    "linguagens": "Linguagens, Códigos e suas Tecnologias",
    "humanas": "Ciências Humanas e suas Tecnologias",
    "natureza": "Ciências da Natureza e suas Tecnologias",
    "matematica": "Matemática e suas Tecnologias",
    "redacao": "Redação",
    "geral": "Preparação geral",
}

SUBJECTS = {
    "portugues": ("linguagens", "Língua Portuguesa"),
    "literatura": ("linguagens", "Literatura"),
    "ingles": ("linguagens", "Inglês"),
    "espanhol": ("linguagens", "Espanhol"),
    "artes": ("linguagens", "Artes"),
    "historia": ("humanas", "História"),
    "geografia": ("humanas", "Geografia"),
    "filosofia": ("humanas", "Filosofia"),
    "sociologia": ("humanas", "Sociologia"),
    "fisica": ("natureza", "Física"),
    "quimica": ("natureza", "Química"),
    "biologia": ("natureza", "Biologia"),
    "matematica": ("matematica", "Matemática"),
    "redacao": ("redacao", "Redação"),
    "geral": ("geral", "Preparação geral"),
}

TOPICS = {
    "ortografia_acentuacao": "redacao", "gramatica": "redacao", "pontuacao": "redacao",
    "interpretacao_tema": "redacao", "estrutura_dissertativa": "redacao", "repertorio": "redacao",
    "tese_argumentos": "redacao", "aprofundamento": "redacao", "projeto_de_texto": "redacao",
    "conectivos": "redacao", "coesao_referencial": "redacao", "paragrafacao": "redacao",
    "proposta_intervencao": "redacao", "detalhamento": "redacao", "direitos_humanos": "redacao",
    "interpretacao_texto": "portugues", "generos_textuais": "portugues", "variacao_linguistica": "portugues",
    "funcoes_linguagem": "portugues", "escolas_literarias": "literatura", "modernismo": "literatura",
    "leitura_ingles": "ingles", "leitura_espanhol": "espanhol", "arte_cultura": "artes",
    "brasil_colonia_imperio": "historia", "brasil_republica": "historia", "historia_geral": "historia",
    "geografia_ambiental": "geografia", "urbanizacao_populacao": "geografia", "cartografia": "geografia",
    "geopolitica": "geografia", "etica_politica": "filosofia", "trabalho_cidadania": "sociologia",
    "mecanica": "fisica", "eletricidade": "fisica", "ondas_optica": "fisica", "termologia": "fisica",
    "estequiometria": "quimica", "quimica_organica": "quimica", "quimica_ambiental": "quimica",
    "solucoes": "quimica", "ecologia": "biologia", "genetica": "biologia", "fisiologia_humana": "biologia",
    "citologia": "biologia", "matematica_basica": "matematica", "porcentagem_razao": "matematica",
    "geometria": "matematica", "estatistica_probabilidade": "matematica", "funcoes": "matematica",
    "interpretacao_graficos": "matematica", "simulado": "geral", "revisao_erros": "geral",
    "estrategia_prova": "geral",
}

REDACAO_TOPIC_COMPETENCY = {
    "ortografia_acentuacao": 1, "gramatica": 1, "pontuacao": 1,
    "interpretacao_tema": 2, "estrutura_dissertativa": 2, "repertorio": 2,
    "tese_argumentos": 3, "aprofundamento": 3, "projeto_de_texto": 3,
    "conectivos": 4, "coesao_referencial": 4, "paragrafacao": 4,
    "proposta_intervencao": 5, "detalhamento": 5, "direitos_humanos": 5,
}

DIFFICULTIES = {"basico": 1, "intermediario": 2, "avancado": 3}


def difficulty_for_level(level: int) -> str:
    return "basico" if level <= 80 else "intermediario" if level <= 120 else "avancado"


@dataclass(frozen=True)
class Activity:
    id: str
    area: str
    subject: str
    competency: int | None
    level: int | None
    difficulty: str
    minutes: int
    topics: tuple[str, ...]
    title: str
    description: str

    @property
    def subject_label(self) -> str:
        return SUBJECTS[self.subject][1]

    @property
    def search_text(self) -> str:
        return (f"{AREAS[self.area]} — {self.subject_label}. {self.title}. {self.description} "
                f"Tópicos: {', '.join(self.topics)}.")


def load_catalog(catalog_dir: Path) -> dict[str, Activity]:
    activities: dict[str, Activity] = {}
    for path in sorted(catalog_dir.glob("*.md")):
        if path.name.lower() == "readme.md":
            continue
        for match in _ENTRY.finditer(path.read_text(encoding="utf-8").replace("\r\n", "\n")):
            activity = _activity(_parse_meta(match.group("meta")), match.group("body").strip())
            if activity.id in activities:
                raise ValueError(f"Atividade duplicada no catálogo: {activity.id}")
            activities[activity.id] = activity
    return activities


def _activity(meta: dict[str, str], description: str) -> Activity:
    activity_id = meta.get("id", "?")
    try:
        subject = meta["subject"]
        area = SUBJECTS[subject][0]
        competency = int(meta["competency"]) if meta.get("competency") else None
        level = int(meta["level"]) if meta.get("level") else None
        difficulty = meta.get("difficulty") or (difficulty_for_level(level) if level is not None else "")
        topics = tuple(t.strip() for t in meta.get("topics", "").split(",") if t.strip())
        activity = Activity(activity_id, area, subject, competency, level, difficulty, int(meta["minutes"]),
                            topics, meta["title"], description)
    except (KeyError, ValueError) as error:
        raise ValueError(f"Atividade inválida no catálogo: {activity_id} ({error})") from error
    problems = [t for t in topics if TOPICS.get(t) != subject]
    if (problems or difficulty not in DIFFICULTIES or activity.minutes <= 0 or not description
            or (competency is not None and (subject != "redacao" or not 0 <= competency <= 5))):
        raise ValueError(f"Atividade inválida no catálogo: {activity_id} (tópicos fora da disciplina: {problems})")
    return activity
