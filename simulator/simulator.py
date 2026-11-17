#!/usr/bin/env python3
"""
EnviMesh simulator: virtual sensor nodes that send readings to Supabase.

Uses only the Python standard library (no pip install needed).

Scenarios (5 nodes):
  Node-01  dries out slowly -> warning (>= 40 cm) then danger (>= 50 cm)
  Node-02  stable, wet peat
  Node-03  hovers around the warning threshold
  Node-04  sometimes reports a sensor error
  Node-05  sends 3 readings, then goes silent (shows as offline)

Usage (from this folder):
  python simulator.py            run forever, one round every SIM_TICK_SECONDS
  python simulator.py --once     send one round and stop (quick test)
  python simulator.py --reset    delete the simulated nodes and all their data, then stop

Configuration is read from a .env file next to this script (see .env.example).
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

def load_env(path: Path) -> None:
    """Minimal .env reader: KEY=VALUE lines, # comments, optional quotes."""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key.strip(), value)


load_env(HERE / ".env")

SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY = os.environ.get("SUPABASE_SECRET_KEY", "")
CENTER_LAT = float(os.environ.get("SIM_CENTER_LAT", "-0.3500"))   # default: peat area, Kubu Raya, West Kalimantan
CENTER_LON = float(os.environ.get("SIM_CENTER_LON", "109.4500"))
RADIUS_M = float(os.environ.get("SIM_RADIUS_M", "1500"))
TICK_SECONDS = float(os.environ.get("SIM_TICK_SECONDS", "30"))
WAKE_INTERVAL_MIN = int(os.environ.get("SIM_WAKE_INTERVAL_MIN", "1"))
SEED = int(os.environ.get("SIM_SEED", "42"))

NODE_IDS = ["Node-01", "Node-02", "Node-03", "Node-04", "Node-05"]


# ---------------------------------------------------------------------------
# Supabase REST helper
# ---------------------------------------------------------------------------

def api(method: str, path: str, body=None, prefer: str = "return=minimal"):
    """Call the Supabase Data API (PostgREST)."""
    headers = {
        "apikey": SUPABASE_KEY,
        "Content-Type": "application/json",
        "Prefer": prefer,
    }
    # Legacy JWT keys (eyJ...) also go in Authorization. New sb_secret_ keys must NOT.
    if SUPABASE_KEY.startswith("eyJ"):
        headers["Authorization"] = f"Bearer {SUPABASE_KEY}"

    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            text = resp.read().decode("utf-8")
            if not text:
                return None
            try:
                return json.loads(text)
            except ValueError:
                return text
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {detail}") from None


# ---------------------------------------------------------------------------
# Simulation model
# ---------------------------------------------------------------------------

rng = random.Random(SEED)


def point_near(lat: float, lon: float, radius_m: float):
    d = radius_m * math.sqrt(rng.random())
    b = rng.random() * 2 * math.pi
    dlat = d * math.cos(b) / 111_320.0
    dlon = d * math.sin(b) / (111_320.0 * math.cos(math.radians(lat)))
    return round(lat + dlat, 6), round(lon + dlon, 6)


class VirtualNode:
    def __init__(self, node_id: str, start_water: float, battery: float):
        self.id = node_id
        self.lat, self.lon = point_near(CENTER_LAT, CENTER_LON, RADIUS_M)
        self.water = start_water      # cm below surface
        self.battery = battery
        self.sent = 0

    def step(self, now: datetime) -> dict | None:
        """Next reading for this node, or None when it stays silent."""
        if self.id == "Node-05" and self.sent >= 3:
            return None  # goes offline

        # Daily cycle (WIB = UTC+7): warmest ~14:00, coolest ~05:00.
        hour = (now.hour + 7) % 24 + now.minute / 60
        daily = math.sin((hour - 8) / 24 * 2 * math.pi)        # -1 .. 1
        air_temp = 29.5 + 4.0 * daily + rng.gauss(0, 0.4)
        humidity = 78 - 18 * daily + rng.gauss(0, 2)

        if self.id == "Node-01":                                 # drying out
            self.water = min(self.water + rng.uniform(0.4, 1.2), 75)
        elif self.id == "Node-02":                               # stable, wet
            self.water = 18 + rng.gauss(0, 1.5)
        elif self.id == "Node-03":                               # around the warning line
            self.water = 38 + 4 * math.sin(self.sent / 6) + rng.gauss(0, 1)
        else:                                                    # normal drift
            self.water = max(5, self.water + rng.gauss(0, 0.8))

        error_code = None
        water = round(self.water, 1)
        if self.id == "Node-04" and rng.random() < 0.08:
            error_code = "WATER_SENSOR_TIMEOUT"
            water = None

        soil = max(10, min(95, 92 - self.water * 0.9 + rng.gauss(0, 2)))
        soil_temp = 27 + 0.06 * self.water + 1.2 * daily + rng.gauss(0, 0.2)
        self.battery = max(0, self.battery - rng.uniform(0.02, 0.08))

        possible_fire = bool(
            water is not None and water >= 55 and air_temp >= 32.5 and humidity <= 62
        )

        self.sent += 1
        return {
            "node_id": self.id,
            "measured_at": now.isoformat(),
            "water_level_cm": water,
            "soil_moisture_pct": round(soil, 1),
            "air_temp_c": round(air_temp, 1),
            "air_humidity_pct": round(max(30, min(100, humidity)), 1),
            "soil_temp_c": round(soil_temp, 1),
            "battery_pct": int(round(self.battery)),
            "error_code": error_code,
            "possible_fire": possible_fire,
            "source": "simulator",
        }


def make_nodes():
    return [
        VirtualNode("Node-01", start_water=30, battery=92),
        VirtualNode("Node-02", start_water=18, battery=85),
        VirtualNode("Node-03", start_water=38, battery=77),
        VirtualNode("Node-04", start_water=26, battery=64),
        VirtualNode("Node-05", start_water=22, battery=14),
    ]


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

def register(nodes):
    rows = [
        {
            "id": n.id,
            "name": f"Simulated {n.id}",
            "latitude": n.lat,
            "longitude": n.lon,
            "wake_interval_min": WAKE_INTERVAL_MIN,
            "is_active": True,
        }
        for n in nodes
    ]
    api("POST", "nodes?on_conflict=id", rows, prefer="resolution=merge-duplicates,return=minimal")
    print(f"Registered {len(rows)} nodes around ({CENTER_LAT}, {CENTER_LON}).")


def send_round(nodes):
    now = datetime.now(timezone.utc)
    rows = [r for r in (n.step(now) for n in nodes) if r is not None]
    if rows:
        api("POST", "readings", rows)
    stamp = now.astimezone().strftime("%H:%M:%S")
    summary = "  ".join(
        f"{r['node_id']}={'ERR' if r['water_level_cm'] is None else r['water_level_cm']}"
        for r in rows
    )
    print(f"[{stamp}] sent {len(rows)} readings  {summary}")


def reset():
    ids = ",".join(NODE_IDS)
    api("DELETE", f"nodes?id=in.({ids})")
    print("Deleted simulated nodes and all their readings and alerts.")


def main():
    parser = argparse.ArgumentParser(description="EnviMesh Supabase simulator")
    parser.add_argument("--once", action="store_true", help="send one round and stop")
    parser.add_argument("--reset", action="store_true", help="delete simulated nodes and data, then stop")
    args = parser.parse_args()

    if not SUPABASE_URL or not SUPABASE_KEY:
        sys.exit("Missing SUPABASE_URL or SUPABASE_SECRET_KEY. Copy .env.example to .env and fill it in.")

    if args.reset:
        reset()
        return

    nodes = make_nodes()
    register(nodes)
    if args.once:
        send_round(nodes)
        return

    print(f"Sending a round every {TICK_SECONDS:g} s. Press Ctrl+C to stop.")
    try:
        while True:
            try:
                send_round(nodes)
            except (RuntimeError, urllib.error.URLError, TimeoutError) as e:
                print(f"  ! send failed, will retry next round: {e}")
            time.sleep(TICK_SECONDS)
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
