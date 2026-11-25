from __future__ import annotations

import argparse
import ast
import csv
import hashlib
import io
import json
import random
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

COMMIT = "7f0ea9cf854c8a24474b573ff5cdc02f0c7df4d8"
BASE_URL = f"https://raw.githubusercontent.com/lplnufpi/essay-br/{COMMIT}/extended-corpus"
FILES = {
    "extended_essay-br.csv": "9181c2c06ad5a6bc0af3eade4eae4a78d65da71967333439394489920f57fde3",
    "prompts.csv": "5328fcd9827a3fffdc009179af7c1c1e6bfb7208f4a13cb16f56865906b65deb",
}
SERVICE_DIR = Path(__file__).resolve().parents[1]
CORPUS_OUT = SERVICE_DIR / "corpus" / "essay-br.jsonl"
EVAL_OUT = SERVICE_DIR / "eval" / "essay-br-test.jsonl"
MIN_CHARS, MAX_CHARS = 400, 6000
SEED = 2026


def download(name: str) -> str:
    with urllib.request.urlopen(f"{BASE_URL}/{name}", timeout=60) as response:
        data = response.read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != FILES[name]:
        raise SystemExit(f"Hash inesperado para {name}: {digest}")
    return data.decode("utf-8")


def parse_list(value: str) -> list[str]:
    parsed = ast.literal_eval(value)
    if not isinstance(parsed, list) or not all(isinstance(item, str) for item in parsed):
        raise ValueError("lista de parágrafos inválida")
    return parsed


def load_essays(essays_csv: str, prompts_csv: str) -> list[dict]:
    csv.field_size_limit(sys.maxsize)
    themes = {row["id"]: row["title"].strip() for row in csv.DictReader(io.StringIO(prompts_csv))}
    essays = []
    for index, row in enumerate(csv.DictReader(io.StringIO(essays_csv))):
        try:
            paragraphs = parse_list(row["essay"])
            scores = [int(row[f"c{i}"]) for i in range(1, 6)]
        except (ValueError, SyntaxError):
            continue
        text = "\n\n".join(p.strip() for p in paragraphs if p.strip())
        if not MIN_CHARS <= len(text) <= MAX_CHARS or any(s % 40 or not 0 <= s <= 200 for s in scores):
            continue
        essays.append({
            "id": f"essay-br-{index}",
            "theme": themes.get(row["prompt"], "").strip(),
            "scores": scores,
            "text": text,
        })
    return essays


def stratified_split(essays: list[dict], corpus_size: int, eval_size: int) -> tuple[list[dict], list[dict]]:
    buckets: dict[int, list[dict]] = defaultdict(list)
    for essay in essays:
        buckets[min(sum(essay["scores"]) // 200, 4)].append(essay)
    rng = random.Random(SEED)
    for items in buckets.values():
        rng.shuffle(items)

    def take(size: int) -> list[dict]:
        total = sum(len(items) for items in buckets.values())
        chosen = []
        for key in sorted(buckets):
            quota = round(size * len(buckets[key]) / total) if total else 0
            chosen.extend(buckets[key][:quota])
            buckets[key] = buckets[key][quota:]
        return sorted(chosen, key=lambda e: int(e["id"].rsplit("-", 1)[1]))

    evaluation = take(eval_size)
    corpus = take(corpus_size)
    random.Random(SEED).shuffle(evaluation)
    return corpus, evaluation


def write_jsonl(path: Path, essays: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as out:
        for essay in essays:
            out.write(json.dumps(essay, ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Importa o corpus Essay-BR (MIT, LPLN/UFPI) para o RAG.")
    parser.add_argument("--corpus-size", type=int, default=1500)
    parser.add_argument("--eval-size", type=int, default=200)
    args = parser.parse_args()

    print(f"Baixando Essay-BR @ {COMMIT[:7]}...")
    essays = load_essays(download("extended_essay-br.csv"), download("prompts.csv"))
    corpus, evaluation = stratified_split(essays, args.corpus_size, args.eval_size)
    write_jsonl(CORPUS_OUT, corpus)
    write_jsonl(EVAL_OUT, evaluation)
    print(f"Válidas: {len(essays)} | RAG: {len(corpus)} → {CORPUS_OUT.relative_to(SERVICE_DIR)}"
          f" | avaliação: {len(evaluation)} → {EVAL_OUT.relative_to(SERVICE_DIR)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
