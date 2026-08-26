"""Catch Flow extensions written as free functions.

`kotlinx.coroutines.flow.first(flow)` compiles nowhere — `first` is an
extension on Flow, so it must be `flow.first()`. The mistake looks
plausible and produces a cascade of unrelated errors ("cannot infer
type", "unresolved reference 'it'"), so it is worth catching by shape.

Deliberately a closed list of known Flow extensions rather than a
general rule: `androidx.compose.runtime.rememberCoroutineScope(...)` and
`androidx.core.content.ContextCompat.getColor(...)` are both legitimate
fully-qualified calls, so anything broader produces false alarms.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import glob, os, re, sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src


FLOW_EXTENSIONS = {
    "first", "firstOrNull", "single", "singleOrNull", "toList", "toSet",
    "collect", "count", "fold", "reduce", "last", "lastOrNull",
}
root = os.environ.get("FQCHECK_ROOT", ".")
pat = re.compile(
    r"(?<![\w.])kotlinx\.coroutines\.flow\.(" + "|".join(sorted(FLOW_EXTENSIONS)) + r")\s*\("
)
bad = []
for f in [str(x) for x in _src.kt_files()]:
    for n, line in enumerate(open(f, encoding="utf-8"), 1):
        t = line.strip()
        if t.startswith(("import ", "//", "*", "/*")):
            continue
        for m in pat.finditer(line):
            bad.append((os.path.basename(f), n, m.group(1)))
if bad:
    print(f"✗ {len(bad)} Flow extension(s) called as a free function:")
    for f, n, fn in bad:
        print(f"   {f}:{n}  kotlinx.coroutines.flow.{fn}(x)  ->  x.{fn}()")
    sys.exit(1)
print("✓ no Flow extensions called as free functions")
