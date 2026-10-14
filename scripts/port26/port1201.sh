#!/bin/bash
# port1201.sh "<subject>" [base]: port main's HEAD commit (src only; or base..HEAD) to forge1.20.1,
# gated on compile and tests, then pushed. Shares ../CrawlSpace-port with port.sh: one at a time.
# Expect hand fixes more often than on 26.x: main writes 1.21 APIs, and fix1201.py only knows the renames.
S=$(cd "$(dirname "$0")" && pwd); cd /mnt/d/Repos/sable/CrawlSpace || exit 1
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
C=$(git rev-parse --short HEAD); BASE=${2:-$C^}; git diff $BASE $C -- src/ > $T/port.diff
git fetch -q; git worktree add -q --detach ../CrawlSpace-port origin/forge1.20.1 || exit 1
cd ../CrawlSpace-port; export JAVA_HOME=/home/sable/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
git apply -3 $T/port.diff 2>&1 | grep -iE "^error"
python3 $S/theirs.py; python3 $S/fix1201.py
if git grep -lI "^<<<<<<< " -- src | grep .; then echo "forge1.20.1 CONFLICTS LEFT (worktree kept at ../CrawlSpace-port)"; exit 1; fi
E=$(bash ./gradlew compileJava 2>&1 | grep -A2 "error:" | grep -v "^--" | head -40)
if [ -n "$E" ]; then echo "== forge1.20.1 does not compile (worktree kept at ../CrawlSpace-port):"; echo "$E"; exit 1; fi
if bash ./gradlew test 2>&1 | grep -E "FAILED"; then echo "forge1.20.1 tests failed"; exit 1; fi
git add -A && git commit -qm "$1

Port of main $C.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01XPzk5ajyyL9sY9xVs5MsZf (forge1.20.1)" && git push -q origin HEAD:forge1.20.1 && echo "forge1.20.1 $(git rev-parse --short HEAD)"
cd /mnt/d/Repos/sable/CrawlSpace && git worktree remove --force ../CrawlSpace-port
