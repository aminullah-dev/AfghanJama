"""Every parameter line in a composable signature must end with a comma.

A script that inserts a parameter can silently drop the comma on the
previous line — a syntax error that brace-balance cannot see.

Comment lines (KDoc, block, line) are legal between parameters and carry
no comma, so they are skipped.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import glob, re, sys, os
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

root = os.environ.get("PARAMCHECK_ROOT", ".")
bad = []
for f in [str(x) for x in _src.kt_files()]:
    s = open(f, encoding="utf-8").read()
    for m in re.finditer(r"fun\s+\w+\(\n((?:[ \t]+[^\n)]*\n)+?)\)", s):
        lines = [
            l for l in m.group(1).split("\n")
            if l.strip() and not l.strip().startswith(("//", "/*", "*"))
        ]
        for l in lines[:-1]:
            t = l.rstrip()
            if not t.endswith(",") and not t.endswith("("):
                bad.append((f.split("/")[-1], t.strip()))
if bad:
    print(f"✗ {len(bad)} missing comma(s) in a parameter list:")
    for f, l in bad: print(f"   {f}: {l}")
    sys.exit(1)
print("✓ all parameter lists comma-terminated")
