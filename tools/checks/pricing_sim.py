#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""قیمت‌دهی: عددی که ماشین‌حساب می‌گوید، همان است که دفتر نشان می‌دهد.

`Margin.percent` سود را با `toInt()` حساب می‌کند که **می‌بُرد**، نه گرد
می‌کند. پس `priceFor` باید به **بالا** گرد کند، وگرنه برگشت یک درصد کم
می‌آورد:

    بهای ۱۰۱، سودِ ۵٪  →  گردکردن به نزدیک‌ترین: ۱۰۶  →  دفتر می‌گوید ۴٪

یعنی ماشین‌حساب یک عدد بگوید و دفتر عددِ دیگری. برای کارفرمایی که هر
روز قیمت می‌زند، همین یک درصد اعتمادش را به هر دو از بین می‌برد.

این بررسی همان قاعده را **مستقل از کد** پیاده می‌کند و بعد می‌سنجد که
`Margin.kt` همان را می‌گوید.
"""
import pathlib as _pl
import re
import sys
import math
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


# ── قاعده، مستقل از کد ───────────────────────────────────────────
def percent(cost, price):
    """همان چیزی که Margin.Line.percent می‌گوید."""
    return int((price - cost) * 100.0 / cost) if cost > 0 else None


def price_for(cost, pct, *, round_near=False, floor=False, no_clamp=False):
    """قاعدهٔ درست — و سه نسخهٔ عمداً خراب برای اثباتِ اینکه بررسی می‌گیرد."""
    if cost <= 0:
        return 0
    p = pct if no_clamp else max(pct, 0)
    raw = cost * (100 + p) / 100.0
    if round_near:
        return round(raw)
    if floor:
        return math.floor(raw)
    return math.ceil(raw)


# ── ۱) برگشت: قیمتی که می‌دهد، همان درصد را پس بدهد ──────────────
COSTS = list(range(100, 4000, 7))
PCTS = list(range(0, 101))

bad = [(c, p) for c in COSTS for p in PCTS if percent(c, price_for(c, p)) != p]
check(not bad, f"برگشتِ درصد شکست: {len(bad)} مورد، مثلاً {bad[:3]}")

# ── ۲) نسخه‌های عمداً خراب باید گرفته شوند ───────────────────────
near = [(c, p) for c in COSTS for p in PCTS
        if percent(c, price_for(c, p, round_near=True)) != p]
check(near, "نسخهٔ «گردکردن به نزدیک‌ترین» باید شکست بخورد ولی نخورد — بررسی پوچ است")

flr = [(c, p) for c in COSTS for p in PCTS
       if percent(c, price_for(c, p, floor=True)) != p]
check(flr, "نسخهٔ «گردکردن به پایین» باید شکست بخورد ولی نخورد — بررسی پوچ است")

# ── ۳) بهای صفر → قیمتِ صفر، نه تقسیم بر صفر ─────────────────────
check(price_for(0, 20) == 0, "بهای صفر باید قیمتِ صفر بدهد")
check(price_for(-5, 20) == 0, "بهای منفی باید قیمتِ صفر بدهد")

# ── ۴) درصدِ منفی به صفر می‌رسد: قیمت هرگز زیرِ بها نمی‌رود ───────
check(price_for(1000, -30) == 1000, "درصدِ منفی باید به صفر برسد و قیمت = بها شود")
check(price_for(1000, -30, no_clamp=True) < 1000,
      "نسخهٔ بی‌محدودیت باید زیرِ بها برود — وگرنه بندِ محدودیت بی‌معناست")

# ── ۵) و حالا: کد همین را می‌گوید؟ ───────────────────────────────
SRC = (_src.CORE / "com/afghanjama/data/Margin.kt").read_text(encoding="utf-8")

check("fun priceFor" in SRC, "Margin.priceFor پیدا نشد")

body = re.search(r"fun priceFor\([^)]*\)[^{]*\{(.*?)\n    \}", SRC, re.S)
check(body is not None, "بدنهٔ priceFor خوانده نشد")
if body:
    b = body.group(1)
    check("ceil(" in b,
          "priceFor باید به بالا گرد کند (ceil) — با گردکردنِ دیگر، درصد یک واحد کم برمی‌گردد")
    check("round(" not in b, "priceFor نباید round بزند — برگشتِ درصد را می‌شکند")
    check("coerceAtLeast(0)" in b, "درصد باید به صفر محدود شود تا قیمت زیرِ بها نرود")
    check("cost <= 0L" in b, "بهای صفر باید جدا گرفته شود، وگرنه تقسیم بر صفر")

if fails:
    print(f"✗ {len(fails)} مشکل در قیمت‌دهی:")
    for f in fails:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ قیمت‌دهی: برگشتِ درصد برای {len(COSTS)}×{len(PCTS)} ترکیب دقیق است، "
      f"و هر ۲ نسخهٔ خرابِ گردکردن گرفته شد")
