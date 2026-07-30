# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import sys, pathlib
bad = 0
for p in sorted(pathlib.Path(_REPO + "/app/src/main/java").rglob("*.kt")):
    s = p.read_text(encoding="utf-8")
    i = 0; n = len(s)
    depth = {"(":0,"{":0,"[":0}
    pairs = {")":"(","}":"{","]":"["}
    while i < n:
        c = s[i]
        if c == "/" and i+1 < n and s[i+1] == "/":
            while i < n and s[i] != "\n": i += 1
        elif c == "/" and i+1 < n and s[i+1] == "*":
            i += 2
            while i+1 < n and not (s[i] == "*" and s[i+1] == "/"): i += 1
            i += 2
        elif s.startswith('"""', i):
            i += 3
            while i < n and not s.startswith('"""', i): i += 1
            i += 3
        elif c == '"':
            i += 1
            while i < n and s[i] != '"':
                if s[i] == "\\": i += 1
                i += 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and s[i] != "'":
                if s[i] == "\\": i += 1
                i += 1
            i += 1
        else:
            if c in depth: depth[c] += 1
            elif c in pairs: depth[pairs[c]] -= 1
            i += 1
    if any(v != 0 for v in depth.values()):
        print("UNBALANCED", p, depth); bad += 1
print("✓ brace/paren balance clean" if not bad else f"{bad} unbalanced files")
