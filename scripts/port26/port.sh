#!/bin/bash
# port.sh "<subject>" [base]: port main's HEAD commit (src only; or base..HEAD) to mc26.1, mc26.2, mc26.3,
# each gated on compile and tests, then pushed. One at a time: the worktree is shared.
S=$(cd "$(dirname "$0")" && pwd); cd /mnt/d/Repos/sable/CrawlSpace || exit 1
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
C=$(git rev-parse --short HEAD); BASE=${2:-$C^}; git diff $BASE $C -- src/ > $T/port.diff
git fetch -q; git worktree add -q --detach ../CrawlSpace-port origin/mc26.1 || exit 1
cd ../CrawlSpace-port; export JAVA_HOME=/home/sable/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2
MSG="$1

Port of main $C.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01XPzk5ajyyL9sY9xVs5MsZf"
for b in mc26.1 mc26.2 mc26.3; do
  git checkout -q -f --detach origin/$b && git apply -3 $T/port.diff 2>&1 | grep -iE "^error"
  python3 $S/theirs.py; python3 $S/fix26.py; [ $b != mc26.1 ] && python3 $S/fix262.py
  if git grep -lI "^<<<<<<< " -- src | grep .; then echo "$b CONFLICTS LEFT"; break; fi
  E=$(bash ./gradlew compileJava 2>&1 | grep -A2 "error:" | grep -v "^--" | head -24)
  if [ -n "$E" ]; then echo "== $b does not compile:"; echo "$E"; break; fi
  if bash ./gradlew test 2>&1 | grep -E "FAILED"; then echo "$b tests failed"; break; fi
  git add -A && git commit -qm "$MSG ($b)" && git push -q origin HEAD:$b && echo "$b $(git rev-parse --short HEAD)"
done
cd /mnt/d/Repos/sable/CrawlSpace && git worktree remove --force ../CrawlSpace-port
