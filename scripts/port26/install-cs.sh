#!/bin/bash
# install-cs.sh <jar>: waits until MobHealth - Forge is not running, then replaces its CrawlSpace jar with <jar>.
# A failed process query counts as "running": a guard that errors must never read as safe.
set -o pipefail
I="/mnt/c/Users/darre/curseforge/minecraft/Instances/MobHealth - Forge"
J="$1"
OUT=$(mktemp)
running() {
    powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.Name -like 'java*' } | ForEach-Object { \$_.CommandLine }" 2>/dev/null | tr -d '\r' > "$OUT" || return 0
    for b in '\' '"' ' '; do grep -qF "Instances\\MobHealth - Forge$b" "$OUT" && return 0; done
    return 1
}
[ -f "$J" ] || { echo "no jar $J"; exit 1; }
until ! running; do sleep 20; done
sleep 5
if running; then echo "relaunched before the copy; not copying"; exit 1; fi
for f in "$I"/mods/crawlspace-*.jar; do [ -e "$f" ] && rm "$f"; done
cp "$J" "$I/mods/" && cmp "$J" "$I/mods/$(basename "$J")" || { echo "CrawlSpace copy failed"; exit 1; }
echo "INSTALLED at $(date +%H:%M): CrawlSpace $(unzip -p "$J" META-INF/MANIFEST.MF | grep -i commit | tr -d "\r"); mods now: $(ls "$I"/mods | grep -iE "crawlspace|legendquest" | tr "\n" " ")"
