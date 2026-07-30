"""No named argument or parameter may appear twice in the same call/signature.

A scripted edit that inserts an argument without checking whether it is
already present produces "Argument already passed for this parameter",
which brace balance and import checks cannot see.
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
    # named arguments in a multi-line call, and parameters in a signature
    for m in re.finditer(r"\b(\w+)\(\n((?:[^()]|\([^()]*\))*?)\n(\s*)\)", s):
        name, body = m.group(1), m.group(2)
        top = re.findall(r"^\s{0,24}(\w+)\s*=(?!=)", body, re.M)
        dup = sorted({a for a in top if top.count(a) > 1})
        if dup: bad.append((f.split("/")[-1], name, dup))
        params = re.findall(r"^\s{0,24}(\w+)\s*:\s*[\w(<]", body, re.M)
        dupp = sorted({a for a in params if params.count(a) > 1})
        if dupp: bad.append((f.split("/")[-1], name+" (signature)", dupp))
if bad:
    print(f"✗ {len(bad)} duplicate argument/parameter site(s):")
    for f,n,d in bad: print(f"   {f}: {n} -> {d}")
    sys.exit(1)
print("✓ no duplicated arguments or parameters")
