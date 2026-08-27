#!/usr/bin/env python3
"""List pending journal notes from data/w124/inbox.json for the agent to answer."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INBOX = ROOT / "data/w124/inbox.json"


def main() -> None:
    data = json.loads(INBOX.read_text())
    pending = [item for item in data.get("items", []) if item.get("status") == "pending"]
    if not pending:
        print("no pending inbox items")
        return
    print(f"{len(pending)} pending")
    for item in pending:
        print("---")
        print(item.get("id"))
        print(f"{item.get('date')} · {item.get('odometer')} km")
        print(item.get("body", "").strip())


if __name__ == "__main__":
    main()
