#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""حرکت: یک ریتم برای کلِ اپ، نه ۳۹ ریتمِ کمی‌متفاوت.

رنگ یک بار این درس را داد: وقتی هر صفحه رنگِ خودش را می‌نوشت، «مسیِ»
فاکتور با «مسیِ» کارتِ موجودی یکی نبود. `palette` آن را بست.

حرکت همان خطر را دارد و بدتر: مدتِ ۱۸۰ در یک صفحه و ۲۵۰ در صفحهٔ بعد
را کسی در diff نمی‌بیند، ولی کاربر حس می‌کند اپ ناهماهنگ است بی آنکه
بتواند بگوید چرا.

این بررسی سه چیز را نگه می‌دارد:

  ۱ هیچ صفحه‌ای مدتِ خودش را ننویسد — همه از `Motion` بخوانند
  ۲ مدت‌های `Motion` در بازهٔ ۱۵۰ تا ۳۰۰ بمانند
  ۳ «کاهشِ حرکت» واقعاً به مدت وصل باشد، نه فقط تعریف شده
"""
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


MOTION = _src.CORE / "com/afghanjama/ui/theme/Motion.kt"
check(MOTION.exists(), "Motion.kt پیدا نشد")
if not MOTION.exists():
    print("✗ Motion.kt نیست")
    sys.exit(1)

SRC = MOTION.read_text(encoding="utf-8")

# ── ۱) مدت‌ها در بازهٔ انسانی ────────────────────────────────────
durations = {
    m.group(1): int(m.group(2))
    for m in re.finditer(r"const val (\w+_MS) = (\d+)", SRC)
}
check(durations, "هیچ مدتی در Motion تعریف نشده")
# `AMBIENT_MS` عمداً بیرونِ بازه است: نمایشِ محیطی پاسخ به لمس نیست.
# استثنا **نام‌دار** است تا عددِ بلندِ بعدی هم بی‌سروصدا رد نشود.
for name, ms in durations.items():
    if name == "AMBIENT_MS":
        check(300 < ms <= 800, f"AMBIENT_MS = {ms} — نمایشِ محیطی هم حدی دارد")
        continue
    check(
        150 <= ms <= 300,
        f"{name} = {ms} بیرونِ بازهٔ ۱۵۰..۳۰۰ است — "
        "زیرِ ۱۵۰ حرکت دیده نمی‌شود، بالای ۳۰۰ اپ کُند حس می‌شود",
    )

# ── ۲) کاهشِ حرکت واقعاً وصل باشد ────────────────────────────────
check("LocalReducedMotion" in SRC, "LocalReducedMotion تعریف نشده")
scale = re.search(r"fun scale\(ms: Int, reduced: Boolean\): Int = (.+)", SRC)
check(scale is not None, "تابعِ scale پیدا نشد — کاهشِ حرکت به مدت وصل نیست")
if scale:
    body = scale.group(1)
    check(
        "if (reduced) 0" in body,
        "scale باید وقتی reduced روشن است صفر بدهد، وگرنه تعریفش تزئینی است",
    )

# specها نباید @Composable باشند: `transitionSpec` در AnimatedContent
# لامبدای غیر-composable است و نسخهٔ اولِ این فایل دقیقاً همان‌جا
# نمی‌کامپایل شد. پرچم باید پارامتر بماند.
# دنبالِ **حاشیه‌نویسی** می‌گردیم نه هر جای متن: بارِ اول همین بند
# روی توضیحی که خودِ داستان را می‌گفت قرمز شد.
check(
    not re.search(r"^\s*@Composable\s*$", SRC, re.M),
    "specهای Motion نباید @Composable باشند — در transitionSpec "
    "صدا زده می‌شوند که غیر-composable است",
)
# **هر** تابعِ عمومیِ spec باید پرچم را بگیرد، نه فقط یکی.
#
# بارِ اول این بند دنبالِ `reduced: Boolean` در کلِ فایل می‌گشت و
# تابعِ خصوصیِ `scale` همان را داشت — پس برداشتنِ پرچم از خودِ specها
# از چشمش می‌افتاد. همان دامِ آشنا: نگهبانِ دیگری که شکلِ درست را دارد.
specs = re.findall(r"fun <T> (\w+)\(([^)]*)\)", SRC)
check(specs, "هیچ تابعِ specی در Motion نیست")
for name, params in specs:
    check(
        "reduced: Boolean" in params,
        f"Motion.{name} پرچمِ reduced را نمی‌گیرد — "
        "بیرونِ بدنهٔ composable قابلِ استفاده نمی‌مانَد",
    )

# ── ۳) هیچ‌کس مدتِ سرخود ننویسد ──────────────────────────────────
#
# `tween(220)` یا `durationMillis = 180` در یک صفحه یعنی همان
# پراکندگی‌ای که پالت جلویش را گرفت.
ADHOC = re.compile(r"tween\s*\(\s*(?:durationMillis\s*=\s*)?(\d+)")
offenders = []
for path in _src.kt_files():
    if path.name == "Motion.kt":
        continue
    text = path.read_text(encoding="utf-8", errors="replace")
    for m in ADHOC.finditer(text):
        line = text[: m.start()].count("\n") + 1
        offenders.append(f"{path}:{line} → tween({m.group(1)})")

check(
    not offenders,
    "مدتِ سرخود بیرونِ Motion: " + "، ".join(offenders[:5])
    + f" ({len(offenders)} مورد) — از Motion.normal()/quick() استفاده کنید",
)

if fails:
    print(f"✗ {len(fails)} مشکل در حرکت:")
    for f in fails:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ حرکت: {len(durations)} مدت همه در بازهٔ ۱۵۰..۳۰۰، "
    "کاهشِ حرکت به مدت وصل، و هیچ مدتِ سرخودی بیرونِ Motion"
)
