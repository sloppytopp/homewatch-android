#!/usr/bin/env python3
"""Reads the phone's saved tracker sightings over adb (debug builds only) and shows which "places" the following detector sees.
Usage: tools/walk_test.py [path/to/adb]      (phone plugged in, USB debugging on)
Mirrors detect/Follow.kt: keep the two in step."""
import sqlite3, subprocess, sys, tempfile, os, datetime
ADB = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/Android/Sdk/platform-tools/adb")
BOOT, SHARE, MATCH, MINNET, MINPLACES, MINSPAN, MINSIGHT = 12, 0.15, 0.5, 2, 3, 30 * 60_000, 2

db = tempfile.mktemp(suffix=".db")
with open(db, "wb") as f:
    f.write(subprocess.run([ADB, "exec-out", "run-as", "io.github.sloppytopp.homewatch", "cat", "databases/homewatch.db"], capture_output=True).stdout)
rows = sqlite3.connect(db).execute("select ts,key,label,place from trail order by ts").fetchall()
if not rows:
    sys.exit("No tracker sightings saved yet (is scanning on, Wi-Fi scanning allowed, and an unknown tracker near the phone?)")

clusters = []   # [counts, n]
def rep(c):
    cnt, n = c
    return set(cnt) if n < BOOT else {k for k, v in cnt.items() if v >= 2 and v / n >= SHARE}
def place(fp):
    if len(fp) < MINNET: return -1
    for i, c in enumerate(clusters):
        if len(rep(c) & fp) / len(fp) >= MATCH:
            for k in fp: c[0][k] = c[0].get(k, 0) + 1
            c[1] += 1
            return i + 1
    clusters.append([{k: 1 for k in fp}, 1]); return len(clusters)

t = lambda ms: datetime.datetime.fromtimestamp(ms / 1000).strftime("%a %H:%M")
seq = [(ts, key, label, place(set(p.split(",")) - {""})) for ts, key, label, p in rows]
for key in sorted({s[1] for s in seq}):
    mine = [s for s in seq if s[1] == key]
    print(f"\n{mine[0][2]}  {key}: {len(mine)} sightings, {t(mine[0][0])} to {t(mine[-1][0])}")
    runs = []   # consecutive stays
    for ts, _, _, p in mine:
        if p < 1: continue
        if runs and runs[-1][0] == p: runs[-1][2] = ts; runs[-1][3] += 1
        else: runs.append([p, ts, ts, 1])
    for p, a, b, n in runs: print(f"   place {p}: {t(a)} - {t(b)}  ({n} sightings)")
    per = {}
    for p, a, b, n in runs:
        d = per.setdefault(p, [a, b, 0]); d[0] = min(d[0], a); d[1] = max(d[1], b); d[2] += n
    good = {p: d for p, d in per.items() if d[2] >= MINSIGHT}
    span = (max(d[1] for d in good.values()) - min(d[0] for d in good.values())) if good else 0
    verdict = len(good) >= MINPLACES and span >= MINSPAN
    print(f"   -> {len(good)} places with 2+ sightings over {span // 60000} min: {'FOLLOWING would alert' if verdict else 'no following alert'}")
