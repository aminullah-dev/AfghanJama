"""فهرستِ ResetPlan باید دقیقاً برابرِ جدول‌های واقعیِ دیتابیس باشد.

جدولی که در هیچ فهرستی نباشد، بی‌سروصدا از ریست جان به در می‌برد و
کارگاه فکر می‌کند همه‌چیز پاک شده."""
# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
import re, glob, sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

ROOT = _src.ANY

# جدول‌های واقعی از @Entityها
real={}
for f in _src.glob('data/entities/*.kt'):
    s=open(f).read()
    for m in re.finditer(r'@Entity\b', s):
        seg=s[m.start(): m.start()+900]
        cls=re.search(r'data class (\w+)', seg)
        if not cls: continue
        t=re.search(r'tableName\s*=\s*"([^"]+)"', seg[:cls.start()])
        real[t.group(1) if t else cls.group(1)]=cls.group(1)

# فهرست‌های ResetPlan
plan=open(ROOT+'/data/ResetPlan.kt').read()
def names(block):
    seg=plan.split(f'val {block} = listOf(')[1].split(')')[0]
    return [x for x in re.findall(r'"([^"]+)"', seg)]
clear, keep = names('CLEAR'), names('KEEP')

bad=[]
dup=[t for t in set(clear)&set(keep)]
if dup: bad.append("در هر دو فهرست: "+", ".join(sorted(dup)))
for lbl,l in (("CLEAR",clear),("KEEP",keep)):
    seen=[x for x in l if l.count(x)>1]
    if seen: bad.append(f"تکراری در {lbl}: "+", ".join(sorted(set(seen))))

listed=set(clear)|set(keep)
missing=sorted(set(real)-listed)
ghost=sorted(listed-set(real))
if missing: bad.append("جدولِ دسته‌بندی‌نشده: "+", ".join(f"{t} ({real[t]})" for t in missing))
if ghost: bad.append("نامِ جدولی که وجود ندارد: "+", ".join(ghost))

if bad:
    print("\n".join("✗ "+b for b in bad)); sys.exit(1)
print(f"✓ هر {len(real)} جدول دسته‌بندی شده — {len(clear)} پاک، {len(keep)} می‌ماند")
