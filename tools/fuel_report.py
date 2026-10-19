#!/usr/bin/env python3
"""Rebuild the fuel-report block in data/w124/analytics.md from state.json."""

from __future__ import annotations

import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / "data" / "w124" / "state.json"
ANALYTICS = ROOT / "data" / "w124" / "analytics.md"
BEGIN = "<!-- FUEL-REPORT:BEGIN -->"
END = "<!-- FUEL-REPORT:END -->"

NORMAL_MIN = 9.0
NORMAL_MAX = 13.0


def intervals(fuel: list[dict]) -> list[dict]:
    fills = sorted(
        (f for f in fuel if not f.get("deleted")),
        key=lambda f: (f["odometer"], f["date"]),
    )
    full = [i for i, f in enumerate(fills) if f.get("full")]
    out: list[dict] = []
    for a, b in zip(full, full[1:]):
        start, end = fills[a], fills[b]
        km = end["odometer"] - start["odometer"]
        if km <= 0:
            continue
        liters = sum(f["liters"] for f in fills[a + 1 : b + 1])
        if liters <= 0:
            continue
        types = [f.get("tripType") or "mixed" for f in fills[a + 1 : b + 1]]
        out.append(
            {
                "from_km": start["odometer"],
                "to_km": end["odometer"],
                "from_date": start["date"],
                "to_date": end["date"],
                "km": km,
                "liters": liters,
                "l100": liters / km * 100.0,
                "trip_types": types,
            }
        )
    return out


def verdict_ru(rows: list[dict]) -> str:
    if len(rows) < 2:
        return "Недостаточно данных: нужно минимум две полные заправки."
    last = rows[-1]
    prev = [r["l100"] for r in rows[:-1]]
    median = statistics.median(prev)
    last_l = last["l100"]
    short = last["km"] < 280 and last_l > 12.5
    rising = last_l > median * 1.12 and (last_l - median) > 0.8
    high = last_l > 13.5
    if short and not rising:
        return (
            "Похоже на стиль езды (короткие/городские интервалы), "
            "а не на внезапную прожорливость мотора."
        )
    if rising and last_l > 11.5:
        return (
            f"Расход растёт: последний интервал {last_l:.1f} против медианы "
            f"{median:.1f} л/100 км. Стоит смотреть свечи, провода, смесь, "
            "подсос и температуру."
        )
    if high:
        return (
            f"Расход высокий ({last_l:.1f} л/100 км) для смешанного цикла "
            "200E M102. Сверить тип поездок; если смешанный/трасса — диагностика."
        )
    return (
        f"Пока в норме для этой машины (ориентир {NORMAL_MIN:.0f}–{NORMAL_MAX:.0f} "
        "л/100 км смешанного цикла)."
    )


def render(state: dict) -> str:
    rows = intervals(state.get("fuel") or [])
    odo = state.get("odometer", {}).get("km")
    lines = [
        f"Текущий пробег: {odo:,} км.".replace(",", " "),
        f"Заправок в журнале: {len(state.get('fuel') or [])}.",
        f"Полных интервалов: {len(rows)}.",
        "",
    ]
    if not rows:
        lines.append(
            "Расход появится после второй полной заправки. "
            "Частичные доливки можно вносить сразу — они войдут в сумму литров."
        )
        return "\n".join(lines)
    last = rows[-1]
    avg = statistics.mean(r["l100"] for r in rows)
    lines.append(
        f"Последний расход: {last['l100']:.1f} л/100 км "
        f"({last['km']} км, {last['liters']:.1f} л)."
    )
    lines.append(f"Средний по интервалам: {avg:.1f} л/100 км.")
    lines.append("")
    lines.append("Интервалы:")
    for r in rows[-8:]:
        lines.append(
            f"- {r['from_date']} → {r['to_date']}: {r['l100']:.1f} л/100 км, "
            f"{r['km']} км, {r['liters']:.1f} л ({r['from_km']}–{r['to_km']})"
        )
    lines.append("")
    lines.append("Вердикт: " + verdict_ru(rows))
    return "\n".join(lines)


def main() -> None:
    state = json.loads(STATE.read_text(encoding="utf-8"))
    block = render(state)
    text = ANALYTICS.read_text(encoding="utf-8")
    if BEGIN not in text or END not in text:
        raise SystemExit("analytics.md is missing FUEL-REPORT markers")
    before, rest = text.split(BEGIN, 1)
    _, after = rest.split(END, 1)
    ANALYTICS.write_text(
        f"{before}{BEGIN}\n{block}\n{END}{after}",
        encoding="utf-8",
    )
    print(block)


if __name__ == "__main__":
    main()
