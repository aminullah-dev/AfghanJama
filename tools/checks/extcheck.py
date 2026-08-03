"""توابعِ الحاقیِ خودِ پروژه که با نقطه صدا زده می‌شوند و ایمپورت نشده‌اند.

نقطهٔ کورِ refcheck: `x.fa()` هیچ نامِ آزادی ندارد، پس بررسیِ «هر نمادِ
استفاده‌شده ایمپورت شده» آن را نمی‌بیند. build روی CI همین را گرفت.
"""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import re, glob, os, sys, collections
import sys as _s2, pathlib as _p2
_s2.path.insert(0, str(_p2.Path(__file__).resolve().parent))
import _src


ROOT = None  # مسیرها از _src می‌آیند

# ۱) همهٔ توابعِ الحاقیِ سطحِ بالای پروژه را پیدا کن: fun Type.name(...)
ext = {}          # نام -> پکیج
for path in [str(x) for x in _src.kt_files()]:
    src = open(path).read()
    pkg = re.search(r'^package\s+([\w.]+)', src, re.M)
    if not pkg: continue
    pkg = pkg.group(1)
    for m in re.finditer(r'^(?:internal |public )?fun\s+(?:<[^>]+>\s*)?[\w.<>?]+\.(\w+)\s*\(', src, re.M):
        ext.setdefault(m.group(1), set()).add(pkg)

bad = []
for path in [str(x) for x in _src.kt_files()]:
    src = open(path).read()
    pkg = re.search(r'^package\s+([\w.]+)', src, re.M)
    pkg = pkg.group(1) if pkg else ''
    imports = set(re.findall(r'^import\s+([\w.]+)', src, re.M))
    star = set(re.findall(r'^import\s+([\w.]+)\.\*', src, re.M))
    body = "\n".join(l for l in src.split('\n') if not l.startswith('import '))
    # حذفِ رشته‌ها و کامنت‌ها تا نامِ داخلشان شمرده نشود
    body = re.sub(r'""".*?"""', '""', body, flags=re.S)
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
    body = re.sub(r'//[^\n]*', '', body)
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)

    for name, pkgs in ext.items():
        if not re.search(r'\.\s*'+name+r'\s*\(', body): continue
        if pkg in pkgs: continue                      # هم‌پکیج، ایمپورت لازم نیست
        ok = any(p+'.'+name in imports for p in pkgs) or any(p in star for p in pkgs)
        if not ok:
            line = next((i+1 for i,l in enumerate(src.split('\n'))
                         if re.search(r'\.\s*'+name+r'\s*\(', l) and not l.strip().startswith('import')), '?')
            bad.append(f"{os.path.relpath(path, ROOT)}:{line}  .{name}()  →  import {sorted(pkgs)[0]}.{name}")

print('\n'.join(sorted(set(bad))) if bad else
      f"✓ {len(ext)} تابعِ الحاقیِ پروژه — همه‌جا ایمپورت شده‌اند")
sys.exit(1 if bad else 0)
