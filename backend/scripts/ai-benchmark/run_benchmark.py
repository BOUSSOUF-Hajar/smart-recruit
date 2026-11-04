#!/usr/bin/env python3
"""
Banc d'essai des modèles d'extraction (architecture §6, phase 8).

Lance, pour chaque modèle demandé, l'extraction des 10 CV fictifs avec le MÊME prompt et le MÊME
schéma JSON que l'application (backend/src/main/resources/prompts), puis compare aux extractions
attendues (expected.json) : précision et rappel des compétences, nombre d'expériences, temps moyen,
respect du schéma, compétences inventées sur le CV piégé.

Le texte envoyé est pseudonymisé comme dans l'application (version simplifiée des mêmes règles).
Bibliothèque standard Python 3.10+ uniquement.

Usage :
    ollama pull qwen3.5:9b && ollama pull llama3.1:8b
    python3 run_benchmark.py qwen3.5:9b llama3.1:8b [--url http://localhost:11434] [--out results.md] [--think non]

Sur GitHub Actions : workflow « Banc d'essai IA » (.github/workflows/ai-benchmark.yml).
"""
import argparse
import json
import pathlib
import re
import time
import unicodedata
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
PROMPTS = HERE.parent.parent / "src" / "main" / "resources" / "prompts"
PROMPT_VERSION = "resume-extraction-v1"


def normalize(name: str) -> str:
    text = unicodedata.normalize("NFKC", name).strip().lower()
    return re.sub(r"\s+", " ", text)


def pseudonymize(text: str, name: str) -> str:
    text = re.sub(r"[\w.+-]+@[\w-]+(\.[\w-]+)+", "[EMAIL]", text)
    text = re.sub(r"(?i)\b(https?://|www\.)\S+|\b(linkedin|github|gitlab)\.com/\S+", "[LIEN]", text)
    text = re.sub(r"(?im)^.*\b(né|née|naissance|born|birth|âge|nationalit|situation familiale|mariée?)\b.*$",
                  "[INFORMATION_PERSONNELLE]", text)

    def phone(match):
        digits = sum(c.isdigit() for c in match.group())
        return "[TÉLÉPHONE]" if 9 <= digits <= 15 else match.group()

    text = re.sub(r"(\+|00)?\d[\d \t.()-]{7,}\d", phone, text)
    text = re.sub(r"(?im)\b(adresse|address)\s*:.*$|^[ \t]*(résidence|lotissement|quartier).*$", "[ADRESSE]", text)
    for part in re.split(r"[\s-]+", name):
        if len(part) >= 2:
            text = re.sub(r"(?i)(?<!\w)" + re.escape(part) + r"(?!\w)", "[CANDIDAT]", text)
    return text


def warm_up(url: str, model: str) -> float:
    """Charge le modèle en mémoire avant de mesurer : sinon le premier CV compte aussi ce chargement."""
    body = {"model": model, "stream": False, "messages": [{"role": "user", "content": "OK"}],
            "options": {"num_predict": 1}}
    request = urllib.request.Request(f"{url}/api/chat", data=json.dumps(body).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
    start = time.monotonic()
    with urllib.request.urlopen(request, timeout=600) as response:
        response.read()
    return time.monotonic() - start


def speed(payload: dict) -> str:
    """Débits mesurés par Ollama : lecture du prompt et génération, en tokens par seconde."""
    def rate(count: str, duration: str) -> str:
        n, ns = payload.get(count, 0), payload.get(duration, 0)
        return f"{n} tokens à {n / (ns / 1e9):.1f}/s" if ns else "?"
    thinking = len(payload.get("message", {}).get("thinking") or "")
    return (f"prompt {rate('prompt_eval_count', 'prompt_eval_duration')}, "
            f"réponse {rate('eval_count', 'eval_duration')}"
            + (f", raisonnement de {thinking} caractères" if thinking else ""))


def extract(url: str, model: str, cv_text: str, think: bool | None) -> tuple[dict | None, float, str]:
    body = {
        "model": model,
        "stream": False,
        "format": json.loads((PROMPTS / f"{PROMPT_VERSION}.schema.json").read_text(encoding="utf-8")),
        "options": {"temperature": 0},
        "messages": [
            {"role": "system", "content": (PROMPTS / f"{PROMPT_VERSION}.txt").read_text(encoding="utf-8")},
            {"role": "user", "content": f"<cv>\n{cv_text}\n</cv>"},
        ],
    }
    if think is not None:  # absent par défaut, comme dans l'application
        body["think"] = think
    request = urllib.request.Request(f"{url}/api/chat", data=json.dumps(body).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
    start = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            payload = json.loads(response.read().decode("utf-8"))
        elapsed = time.monotonic() - start
        print(f"    {speed(payload)}")
        content = payload["message"]["content"]
        result = json.loads(content)
        required = {"skills", "experiences", "educations", "languages", "summary"}
        if not required.issubset(result) or len(result["summary"]) > 600:
            return None, elapsed, "hors schéma"
        return result, elapsed, ""
    except Exception as error:  # noqa: BLE001 - un banc d'essai doit continuer
        return None, time.monotonic() - start, type(error).__name__


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("models", nargs="+")
    parser.add_argument("--url", default="http://localhost:11434")
    parser.add_argument("--out", default=str(HERE / "results.md"))
    parser.add_argument("--think", choices=["oui", "non"],
                        help="force ou coupe le raisonnement des modèles qui en ont un (absent : choix du modèle)")
    args = parser.parse_args()
    think = None if args.think is None else args.think == "oui"
    expected = {k: v for k, v in json.loads((HERE / "expected.json").read_text(encoding="utf-8")).items()
                if not k.startswith("_")}

    lines = ["| Modèle | Précision | Rappel | Expériences exactes | Hors schéma | Piège respecté | Temps moyen (s) |",
             "| --- | --- | --- | --- | --- | --- | --- |"]
    for model in args.models:
        tp = fp = fn = exp_ok = invalid = 0
        trap_ok = True
        times = []
        print(f"{model} : chargement en {warm_up(args.url, model):.1f} s (non compté)")
        for cv_id, truth in expected.items():
            text = (HERE / "cvs" / f"{cv_id}.txt").read_text(encoding="utf-8")
            sent = pseudonymize(text, text.splitlines()[0].strip())
            result, elapsed, error = extract(args.url, model, sent, think)
            times.append(elapsed)
            print(f"{model} {cv_id} : {error or 'ok'} ({elapsed:.1f} s)", flush=True)
            if result is None:
                invalid += 1
                fn += len(truth["skills"])
                continue
            found = {normalize(s.get("name", "")) for s in result["skills"] if s.get("evidence")}
            wanted = {normalize(s) for s in truth["skills"]}
            tp += len(found & wanted)
            fp += len(found - wanted)
            fn += len(wanted - found)
            exp_ok += int(len(result["experiences"]) == truth["experienceCount"])
            if truth.get("trap"):
                forbidden = {normalize(s) for s in truth.get("forbiddenSkills", [])}
                trap_ok = trap_ok and not (found & forbidden)
        precision = tp / (tp + fp) if tp + fp else 0
        recall = tp / (tp + fn) if tp + fn else 0
        label = model if think is None else f"{model} (think={args.think})"
        lines.append(f"| {label} | {precision:.2f} | {recall:.2f} | {exp_ok}/{len(expected)} | {invalid} | "
                     f"{'oui' if trap_ok else 'NON'} | {sum(times) / len(times):.1f} |")

    report = "\n".join(lines)
    pathlib.Path(args.out).write_text(report + "\n", encoding="utf-8")
    print("\n" + report)


if __name__ == "__main__":
    main()
