"""Catch unresolved references to the project's own top-level declarations.

Kotlin resolves a top-level symbol only if it is in the same package or
explicitly imported. This finds every project-defined top-level symbol and
flags files that use one without either.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import glob, re, sys, collections

SRC = _REPO + "/app/src/main/java/com/afghanjama"
files = glob.glob(f"{SRC}/**/*.kt", recursive=True)

# ---- نگهبانِ پوچی ----
# این اسکریپت‌ها با مسیرِ نسبی نوشته شده بودند و از پوشهٔ scratchpad هیچ
# فایلی نمی‌دیدند — یعنی بی‌سروصدا سبز می‌ماندند. همان تله‌ای که برای
# تست‌های CI جلویش را گرفته بودم و برای ابزارِ خودم نه.
if len(files) < 50:
    raise SystemExit(
        f"✗ فقط {{len(files)}} فایل اسکن شد — مسیر اشتباه است و بررسی پوچ بوده"
    )


def strip_code(src):
    """Remove comments and string literals so we only look at real code."""
    out=[];i=0;n=len(src);in_s=in_cl=in_cb=in_ch=False
    while i<n:
        c=src[i];nxt=src[i+1] if i+1<n else ''
        if in_cl:
            if c=='\n': in_cl=False; out.append(c)
            i+=1; continue
        if in_cb:
            if c=='*' and nxt=='/': in_cb=False; i+=2; continue
            i+=1; continue
        if in_s:
            if c=='\\': i+=2; continue
            if c=='"': in_s=False
            i+=1; continue
        if in_ch:
            if c=='\\': i+=2; continue
            if c=="'": in_ch=False
            i+=1; continue
        if c=='/' and nxt=='/': in_cl=True; i+=2; continue
        if c=='/' and nxt=='*': in_cb=True; i+=2; continue
        if c=='"':
            if src[i:i+3]=='"""':
                j=src.find('"""',i+3); j=n if j==-1 else j
                # keep ${...} interpolations from raw strings
                out.append(' '); i=j+3; continue
            in_s=True; i+=1; continue
        if c=="'": in_ch=True; i+=1; continue
        out.append(c); i+=1
    return ''.join(out)

def interpolations(src):
    """Expressions inside "${ ... }" are real code even though they sit in strings."""
    return " ".join(re.findall(r"\$\{([^}]*)\}", src)) + " " + \
           " ".join(re.findall(r"\$([A-Za-z_][A-Za-z0-9_.]*)", src))

# ---- 1. map every top-level declaration to its package ----
decl_pkg = {}          # plain top-level symbol -> package
ext_pkg  = {}          # extension function -> package (called with a dot)
pkg_of_file = {}
body_of = {}
for f in files:
    raw = open(f).read()
    pkg = re.search(r"^package\s+([\w.]+)", raw, re.M).group(1)
    pkg_of_file[f] = pkg
    code = strip_code(raw)
    body_of[f] = code + " " + interpolations(raw)
    for m in re.finditer(r"^(?:@\w+(?:\([^)]*\))?\s*)*"
                         r"(?:public |internal |open |abstract |sealed |data |value )*"
                         r"(fun|val|const val|class|object|interface|enum class)\s+"
                         r"(?:<[^>]+>\s*)?"
                         r"([A-Za-z_]\w*)(?![\w.])", code, re.M):
        # the negative lookahead drops extension receivers: in "fun Long.afn()"
        # the first identifier is Long, which is a receiver type, not a decl.
        decl_pkg.setdefault(m.group(2), pkg)
    # extension functions: fun Long.afn() -> called as x.afn(), still needs an import
    for m in re.finditer(r"^(?:public |internal )*fun\s+(?:<[^>]+>\s*)?"
                         r"[A-Za-z_][\w.<>?]*\.([A-Za-z_]\w*)\s*\(", code, re.M):
        ext_pkg.setdefault(m.group(1), pkg)

# ---- 2. check usage ----
problems = []
for f in files:
    pkg = pkg_of_file[f]
    raw = open(f).read()
    imported = set()
    for m in re.finditer(r"^import\s+([\w.*]+)(?:\s+as\s+(\w+))?", raw, re.M):
        path, alias = m.group(1), m.group(2)
        imported.add(alias or path.split(".")[-1])
        if path.endswith(".*"):
            imported.add("*")
    local = set(re.findall(
        r"\b(?:fun|val|var|const val|class|object|interface|enum class)\s+([A-Za-z_]\w*)",
        body_of[f]))
    body = body_of[f]
    # (?<![.\w]) drops member accesses like order.qty / it.name
    used = set(re.findall(r"(?<![.\w])([A-Za-z_]\w*)\b", body))
    # extension calls: `.afn(` — dotted, but an import is still required
    for sym, owner in ext_pkg.items():
        if owner == pkg: continue
        if sym in imported or "*" in imported: continue
        if sym in local: continue
        if not re.search(r"\.\s*" + re.escape(sym) + r"\s*\(", body): continue
        if f"{owner}.{sym}" in raw: continue
        problems.append((f.replace(SRC+"/",""), sym, owner))

    for sym in used:
        owner = decl_pkg.get(sym)
        if owner is None: continue          # not one of ours
        if owner == pkg: continue           # same package, no import needed
        if sym in imported or "*" in imported: continue
        if sym in local: continue
        # fully-qualified usage?
        if f"{owner}.{sym}" in raw: continue
        problems.append((f.replace(SRC+"/",""), sym, owner))

if not problems:
    print(f"✓ {len(files)} files — every project symbol used is imported or same-package")
else:
    print(f"✗ {len(problems)} unresolved reference(s):\n")
    for f, sym, owner in sorted(set(problems)):
        print(f"   {f}")
        print(f"      '{sym}'  needs  import {owner}.{sym}")
    sys.exit(1)
