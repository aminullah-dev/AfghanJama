"""No named argument passed twice, and no parameter declared twice.

A scripted edit that inserts a parameter or argument without checking
whether one already exists produces "Argument already passed for this
parameter" — invisible to brace balance and to import checking.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import glob, re, sys
bad=[]
for f in glob.glob(_REPO + "/app/src/main/java/com/afghanjama/**/*.kt", recursive=True):
    s=open(f).read()
    # call sites: Foo( ... ) with named args at one indent level
    for m in re.finditer(r"^([ \t]*)([A-Z]\w*)\(\n((?:.*\n)+?)\1\)", s, re.M):
        args=re.findall(r"^" + re.escape(m.group(1)) + r"[ \t]{4}(\w+)\s*=(?!=)", m.group(3), re.M)
        for a in set(args):
            if args.count(a)>1: bad.append((f.split("/")[-1], f"{m.group(2)}(...)", f"argument '{a}'"))
    # declarations: fun foo( ... )
    for m in re.finditer(r"fun\s+(\w+)\(\n((?:[ \t]+[^\n)]*\n)+?)\)", s):
        ps=re.findall(r"^[ \t]+(\w+)\s*:", m.group(2), re.M)
        for p in set(ps):
            if ps.count(p)>1: bad.append((f.split("/")[-1], f"fun {m.group(1)}", f"parameter '{p}'"))
    # duplicate named params inside one composable call (navigationIcon etc.)
if bad:
    print(f"✗ {len(bad)} duplicate(s):")
    for f,w,d in sorted(set(bad)): print(f"   {f}: {w} — {d}")
    sys.exit(1)
print("✓ no duplicate arguments or parameters")
