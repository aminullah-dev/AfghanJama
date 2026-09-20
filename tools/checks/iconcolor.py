#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""نشانِ اپ با پالت هم‌رنگ بماند، و پیراهنش روی زمینه‌اش خوانده شود.

**چرا لازم شد.** رنگ‌های نشان هگزِ ثابت در XMLِ اندروید هستند و هیچ
راهی ندارند که از `Brand.kt` بخوانند — منابعِ اندروید کاتلین نمی‌فهمند.
یعنی همان شکافی که پالت و مقیاسِ قلم داشتند: دو جا که باید یکی باشند و
هیچ‌چیز وادارشان نمی‌کند.

این نشان تا امروز دو بار عقب افتاده: اول سبزِ رباتِ اندروید مانده بود
وقتی اپ سبزِ خودش را داشت، بعد سبزِ قدیمی ماند وقتی پالت مرجانی شد.
بارِ سوم را این بررسی می‌گیرد.

دو بند:

  ۱ هر رنگی که در نشان است، در `Brand.kt` هم باشد
  ۲ پیراهن روی **هر دو سرِ** گرادیانِ زمینه از حدِ AA بگذرد
"""
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

AA_TEXT = 4.5

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


def srgb(c):
    x = c / 255
    return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4


def lum(h):
    r, g, b = (h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF
    return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


BRAND = _src.CORE / "com/afghanjama/ui/theme/Brand.kt"
check(BRAND.exists(), "Brand.kt نیست")
if not BRAND.exists():
    print("✗ Brand.kt نیست")
    sys.exit(1)

brand_src = BRAND.read_text(encoding="utf-8")
brand = {
    m.group(1): int(m.group(2), 16) & 0xFFFFFF
    for m in re.finditer(r"val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", brand_src)
}
check(brand, "هیچ رنگی از Brand.kt خوانده نشد — قالب عوض شده و بررسی پوچ می‌شد")

# مسیر از `_src` می‌آید نه دستی — `purecheck` همین را می‌خواهد.
# منابعِ اندروید کنارِ ریشهٔ کاتلینِ :app نشسته‌اند.
#
# تا دیروز اینجا `ROOTS[1]` نوشته بود و درست کار می‌کرد. بعد `:core`
# چندسکویی شد، دو ریشهٔ تازه به ابتدای فهرست آمد، و `ROOTS[1]` شد
# `core/src/jvmAndroidMain` — یعنی این بررسی دنبالِ آیکن در پوشهٔ کاتلینِ
# :core می‌گشت. همان چیزی که توضیحِ `_src.py` هشدارش را داده بود.
RES = _src.APP.parent / "res/drawable"
BG = (RES / "ic_launcher_background.xml").resolve()
FG = (RES / "ic_launcher_foreground.xml").resolve()
for f in (BG, FG):
    check(f.exists(), f"{f.name} پیدا نشد")
if fails:
    print("✗ " + "؛ ".join(fails))
    sys.exit(1)

HEX = re.compile(r'(?:android:(?:fillColor|color))="#([0-9A-Fa-f]{6})"')
bg_hex = [int(h, 16) for h in HEX.findall(BG.read_text(encoding="utf-8"))]
fg_hex = [int(h, 16) for h in HEX.findall(FG.read_text(encoding="utf-8"))]

check(bg_hex, "زمینهٔ نشان هیچ رنگی ندارد")
check(len(fg_hex) == 1, f"نشانه باید یک رنگ داشته باشد، {len(fg_hex)} دارد")

# ── ۱) هر رنگِ نشان از خانوادهٔ پالت باشد ────────────────────────
#
# رنگِ نشان لازم نیست **عیناً** یکی از رنگ‌های Brand باشد — سرِ روشنِ
# گرادیان عمداً پله‌ای بینِ آن‌هاست. ولی باید در بازهٔ همان خانواده
# بماند: نزدیک‌تر از فاصلهٔ ۵۰ به یکی از رنگ‌های Brand.
def near(c):
    def dist(a, b):
        return sum(abs(((a >> s) & 0xFF) - ((b >> s) & 0xFF)) for s in (16, 8, 0))
    return min((dist(c, v), k) for k, v in brand.items())

for c in bg_hex + fg_hex:
    d, k = near(c)
    check(d <= 50,
          f"#{c:06X} در نشان به هیچ رنگِ Brand نزدیک نیست "
          f"(نزدیک‌ترین {k}، فاصله {d}) — نشان از پالت جا مانده")

# ── ۲) پیراهن روی هر دو سرِ گرادیان خوانده شود ───────────────────
if fg_hex:
    shirt = fg_hex[0]
    for c in bg_hex:
        r = ratio(shirt, c)
        check(r >= AA_TEXT,
              f"نشانه روی #{c:06X} نسبتِ {r:.2f} می‌دهد (حد {AA_TEXT}) — "
              "در اندازهٔ کوچکِ صفحهٔ گوشی گم می‌شود")

if fails:
    print(f"✗ {len(fails)} مشکل در نشانِ اپ:")
    for f in fails:
        print(f"  • {f}")
    sys.exit(1)

worst = min(ratio(fg_hex[0], c) for c in bg_hex)
print(f"✓ نشان: {len(bg_hex) + 1} رنگ همه از خانوادهٔ پالت، "
      f"و نشانه روی بدترین سرِ گرادیان {worst:.2f} می‌دهد")
