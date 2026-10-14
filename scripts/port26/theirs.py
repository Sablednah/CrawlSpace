# Resolve `git apply -3` conflicts under src/ by keeping the incoming ("theirs") side.
import re, glob
for p in glob.glob("src/**/*.java", recursive=True):
    s = open(p).read()
    if "<<<<<<< " not in s:
        continue
    s = re.sub(r"<<<<<<< [^\n]*\n.*?=======\n(.*?)>>>>>>> [^\n]*\n", r"\1", s, flags=re.S)
    open(p, "w").write(s)
    print("resolved", p)
