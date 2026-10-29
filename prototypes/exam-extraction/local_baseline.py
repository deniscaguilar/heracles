#!/usr/bin/env python3
"""Linha de base LOCAL da P3: reconhecer o tipo e extrair campos sem enviar nada a ninguém.

Usa só a biblioteca padrão do Python, o pdftotext (poppler) e o tesseract, tudo na máquina.
Lê os exames de local/exams e os gabaritos de local/gabaritos (ambos fora do git).
Imprime apenas métricas agregadas; os detalhes (com valores) vão para local/work/ (fora do git).

Uso: local_baseline.py [raiz_do_repositorio]
"""
import json
import re
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
EXAMS = ROOT / "local" / "exams"
GAB = ROOT / "local" / "gabaritos"
WORK = ROOT / "local" / "work"

UNIT = (r"(?:10\^\d/µ ?L|/µL|%|g/dL|fL|pg|mg/dL|µg/dL|ng/mL|pg/mL|ng/dL|µUI/mL|mUI/mL|U/L|UI/mL|U/mL|"
        r"mL/min/1,73m²|nmol/L|U/g Hb|mg/L|mmol/L|g/L)")
NUM = r"(?:Inferior a |Superior a )?\d[\d\.]*(?:,\d+)?"


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def read_text(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
        return subprocess.run(["pdftotext", "-raw", str(path), "-"], capture_output=True, text=True).stdout
    out = subprocess.run(["tesseract", str(path), "-", "-l", "por", "--psm", "6"],
                         capture_output=True, text=True)
    return out.stdout


def classify(text: str) -> str:
    t = norm(text)
    if "inbody" in t or "composicao corporal" in t or "impedancia" in t and "gordura" in t:
        return "bioimpedancia"
    if "impressao diagnostica" in t or "tecnica" in t and "analise" in t:
        return "imagem"
    if "intervalo de referencia" in t and "resultado" in t:
        return "sangue"
    return "desconhecido"


def extract_blood(text: str):
    rows, prev = [], ""
    for line in text.split("\n"):
        s = line.strip()
        m = re.match(r"^(\*?[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ0-9 ,\-\(\)\./º]{1,60}?) (" + NUM + r") ?(" + UNIT + r")\b", s)
        if m:
            rows.append((m.group(1).strip().lstrip("*"), m.group(2), m.group(3)))
        else:
            m2 = re.match(r"^(" + NUM + r") ?(" + UNIT + r")\b", s)  # nome na linha anterior
            if m2 and prev and re.match(r"^[A-Za-zÀ-ÿ]", prev) and len(prev) < 50:
                rows.append((prev.lstrip("*"), m2.group(1), m2.group(2)))
        if s:
            prev = s
    return rows


def extract_imaging(text: str):
    t = norm(text)
    modalidade = ("angiotomografia coronariana" if "angiotomografia" in t else
                  "ressonancia magnetica" if "ressonancia magnetica" in t else
                  "tomografia" if "tomografia" in t else "ultrassom" if "ultrassom" in t or "ultrassonografia" in t else "")
    has_imp = "impressao diagnostica" in t
    m = re.search(r"IMPRESS[ÃA]O DIAGN[ÓO]STICA:?(.*)", text, re.S | re.I)
    impressao = m.group(1) if m else ""
    ag = re.search(r"[Ee]score total de c[áa]lcio [ée] ([\d,]+)", text)
    return modalidade, has_imp, impressao, ({"escore_calcio_agatston": ag.group(1)} if ag else {})


BIO_LABELS = {
    "altura_cm": r"(\d{3}) ?cm",
    "peso_kg": r"Peso[^\d]{0,15}(\d{2,3},\d)",
    "massa_muscular_esqueletica_kg": r"(?:Esquel[ée]tica|MME)[^\d]{0,15}(\d{2},\d)",
    "pgc_percent": r"PGC[^\d]{0,15}(\d{1,2},\d)",
    "imc": r"IMC[^\d]{0,25}(\d{2},\d)",
    "taxa_metabolica_basal_kcal": r"(?:Taxa Metab[óo]lica Basal|Basal)[^\d]{0,15}(\d{4})",
    "relacao_cintura_quadril": r"(0,\d{2})",
}


def extract_bio(text: str):
    out = {}
    for k, rx in BIO_LABELS.items():
        m = re.search(rx, text, re.I)
        if m:
            out[k] = m.group(1)
    return out


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    detail, summary = {}, []
    files = {
        "blood1": EXAMS / "blood/Laudo Completo 01_12_2025.pdf",
        "blood2": EXAMS / "blood/Laudo Completo 14_08_2026.pdf",
        "other1": EXAMS / "others/4eddc748-0f01-42b8-a29e-a6e7fe47c0f4.pdf",
        "other2": EXAMS / "others/db8ff569-accc-4ac4-95ea-7239fd400285.pdf",
        "bioimpedance": EXAMS / "bioimpedance/bioimpedance.jpeg",
    }
    type_ok = 0
    for name, path in files.items():
        gab = json.loads((GAB / f"{name}.json").read_text(encoding="utf-8"))
        text = read_text(path)
        got_type = classify(text)
        ok_type = got_type == gab["tipo"]
        type_ok += ok_type
        line = {"arquivo": name, "tipo_esperado": gab["tipo"], "tipo_obtido": got_type}
        if gab["tipo"] == "sangue":
            rows = extract_blood(text)
            got = {(norm(n), v, u) for n, v, u in rows}
            exp = {(norm(m["nome"]), m["valor"], m["unidade"]) for m in gab["marcadores"]}
            tp = len(got & exp)
            got_vu = {(v, u) for _, v, u in got}   # leniente: ignora o nome, compara só valor e unidade
            exp_vu = {(v, u) for _, v, u in exp}
            line.update({"esperados": len(exp), "extraidos": len(got), "acertos": tp,
                         "precisao": round(tp / len(got), 3) if got else 0.0,
                         "recall": round(tp / len(exp), 3),
                         "recall_valor_e_unidade": round(len(got_vu & exp_vu) / len(exp_vu), 3)})
            detail[name] = {"faltaram": sorted(exp - got), "a_mais": sorted(got - exp)}
        elif gab["tipo"] == "imagem":
            modalidade, has_imp, impressao, campos = extract_imaging(text)
            frases = gab["frases_impressao"]
            hit = sum(1 for f in frases if norm(f) in norm(impressao))
            line.update({"modalidade_ok": norm(modalidade) == norm(gab["modalidade"]),
                         "impressao_encontrada": has_imp == gab["tem_impressao_diagnostica"],
                         "frases_da_impressao": f"{hit}/{len(frases)}",
                         "campos_numericos": f"{sum(1 for k, v in gab['campos'].items() if campos.get(k) == v)}/{len(gab['campos'])}"})
        else:
            got = extract_bio(text)
            exp = {k: v for k, v in gab["campos"].items() if k in BIO_LABELS}
            hit = sum(1 for k, v in exp.items() if got.get(k) == v)
            line.update({"campos_esperados": len(gab["campos"]), "campos_avaliados": len(exp), "acertos": hit})
            detail[name] = {"obtido": got, "esperado": exp}
        summary.append(line)
    (WORK / "baseline_detail.json").write_text(json.dumps(detail, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"reconhecimento_do_tipo": f"{type_ok}/{len(files)}", "arquivos": summary}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
