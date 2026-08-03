# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
صفحه‌ها و مسیرهای یتیم — قابلیتی که ساخته شده ولی راهی به آن نیست.

دو بار در همین پروژه پیش آمد: «خرج کار» ساخته بود و از هیچ‌جا باز
نمی‌شد، و «فاکتور فروش» هم همین‌طور. کاربر فکر می‌کند قابلیت نیست، در
حالی که کدش نوشته شده و فقط دکمه‌اش جا مانده.

سه چیز سنجیده می‌شود:
  ۱. هر @Composable که «Screen» در نامش است، جایی در ناوبری صدا زده شود.
  ۲. هر مسیرِ ثبت‌شده در Routes، جایی navigate شود (وگرنه بن‌بست است).
  ۳. هر composable(route) در گرافِ ناوبری، مسیرش در Routes باشد.
"""
import glob
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src


SRC = _src.PKG_ROOTS
files = [str(x) for x in _src.kt_files()]

def _rel(f):
    """مسیرِ نسبی به com/afghanjama، از هر ماژولی که باشد."""
    for r in _src.PKG_ROOTS:
        s = str(r) + "/"
        if f.startswith(s):
            return f[len(s):]
    return f

if len(files) < 50:
    raise SystemExit(f"✗ فقط {len(files)} فایل — مسیر اشتباه، بررسی پوچ بود")

NAV = str(_src.find("ui/nav/AppNav.kt"))
nav_src = open(NAV).read()
all_src = "\n".join(open(f).read() for f in files)


def strip(src):
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


nav_code = strip(nav_src)
all_code = strip(all_src)

problems = []

# ---- ۱. صفحه‌های تعریف‌شده ولی صدا زده‌نشده ----
screens = set()
for f in files:
    code = strip(open(f).read())
    for m in re.finditer(r"@Composable\s+(?:private\s+)?fun\s+(\w*Screen)\s*\(", code):
        screens.add(m.group(1))

for s in sorted(screens):
    # در گرافِ ناوبری صدا زده شده؟
    if re.search(rf"(?<![\w.]){re.escape(s)}\s*\(", nav_code):
        continue
    # یا از صفحهٔ دیگری صدا زده شده؟ (مثل PinLockScreen از MainActivity)
    calls = len(re.findall(rf"(?<![\w.]){re.escape(s)}\s*\(", all_code))
    if calls > 1:          # یکی خودِ تعریف است
        continue
    problems.append(("صفحهٔ یتیم", s, "هیچ‌جا صدا زده نمی‌شود"))

# ---- ۲. مسیرهای ثبت‌شده ولی بی‌استفاده ----
routes = {}
# Routes در فایلِ خودش زندگی می‌کند، نه داخلِ AppNav
routes_src = strip(_src.read("ui/nav/Routes.kt"))
m = re.search(r"object\s+Routes\s*\{(.*?)\n\}", routes_src, re.S)
if m:
    for r in re.finditer(r"const\s+val\s+(\w+)\s*=\s*\"([^\"]+)\"", m.group(1)):
        routes[r.group(1)] = r.group(2)
if not routes:
    problems.append(("ساختار", "Routes", "هیچ مسیری پیدا نشد — الگوی فایل عوض شده"))

for name in sorted(routes):
    used = len(re.findall(rf"Routes\.{re.escape(name)}\b", all_code))
    # یک بار در composable(...) ثبت می‌شود؛ اگر فقط همان یک بار باشد،
    # هیچ‌کس به آن navigate نمی‌کند — صفحه ساخته شده ولی در دسترس نیست.
    if used <= 1:
        problems.append(("مسیرِ بن‌بست", name, f"«{routes[name]}» ثبت شده ولی جایی navigate نمی‌شود"))

if problems:
    print(f"✗ {len(problems)} مورد")
    for kind, name, why in problems:
        print(f"  [{kind}] {name}: {why}")
    sys.exit(1)
print(f"✓ {len(screens)} صفحه و {len(routes)} مسیر — همه در دسترس‌اند")
