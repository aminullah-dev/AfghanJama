#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""مقیاسِ قلم: یک تعریف، هر دو سکو.

این همان اشکالی است که یک بار سرِ رنگ افتاد و `palette` بستش: صفحه‌های
مشترک اندازه را از تم می‌گیرند، و اگر هر سکو تمِ خودش را بسازد، همان
صفحه با همان کد دو جور دیده می‌شود.

سرِ قلم دو بار افتاد. بارِ اول `fontFamily` بود و درست شد. بارِ دوم
**اندازه**: ویندوز `Typography()`ِ پیش‌فرضِ متریال را می‌گرفت و فقط
قلمش را عوض می‌کرد، پس `titleLarge` روی گوشی ۲۰ بود و روی پی‌سی ۲۲.

سه بند:

  ۱ مقیاس در `:core` باشد و هر ۱۵ نقش را بدهد
  ۲ هیچ سکویی `Typography()`ِ خام نسازد
  ۳ ارتفاعِ خطِ متنِ اصلی برای فارسی کافی باشد
"""
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

def code_only(text):
    """توضیح‌ها را برمی‌دارد تا بند روی **کد** بنشیند نه روی متن.

    دو بار در همین بررسی لازم شد: یک بار `vazirTypography()` با
    `Typography()` اشتباه گرفته شد، و یک بار توضیحی که خودِ داستان را
    می‌گفت. بندی که متنِ توضیح را می‌خواند، دیر یا زود به نوشتهٔ کسی
    قرمز می‌شود که هیچ کاری نکرده.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


SHARED = _src.CORE / "com/afghanjama/ui/theme/Typography.kt"
check(SHARED.exists(), "Typography.kt مشترک در :core نیست")
if not SHARED.exists():
    print("✗ مقیاسِ مشترک نیست")
    sys.exit(1)

SRC = SHARED.read_text(encoding="utf-8")

# ── ۱) هر ۱۵ نقشِ متریال ─────────────────────────────────────────
ROLES = [
    "displayLarge", "displayMedium", "displaySmall",
    "headlineLarge", "headlineMedium", "headlineSmall",
    "titleLarge", "titleMedium", "titleSmall",
    "bodyLarge", "bodyMedium", "bodySmall",
    "labelLarge", "labelMedium", "labelSmall",
]
missing = [r for r in ROLES if f"{r} = TextStyle" not in SRC]
check(
    not missing,
    "نقشِ تعریف‌نشده به پیش‌فرضِ متریال برمی‌گردد: " + "، ".join(missing),
)

check(
    "fun appTypography(family: FontFamily)" in SRC,
    "appTypography باید قلم را پارامتر بگیرد — هر سکو جور دیگری بارش می‌کند",
)

# ── ۲) هیچ‌کس Typography()ِ خام نسازد ────────────────────────────
#
# `Typography()` بی‌آرگومان یعنی پیش‌فرضِ متریال — همان چیزی که ویندوز
# را از اندروید جدا کرده بود.
raw = []
for path in _src.kt_files():
    if path.name == "Typography.kt":
        continue
    text = code_only(path.read_text(encoding="utf-8", errors="replace"))
    # نگهبانِ پیش از نام لازم است، وگرنه `vazirTypography()` هم
    # «خام» شمرده می‌شود — بارِ اول دقیقاً همین شد.
    for m in re.finditer(r"(?<![A-Za-z0-9_])Typography\(\s*\)", text):
        line = text[: m.start()].count("\n") + 1
        raw.append(f"{path.name}:{line}")
check(
    not raw,
    "Typography()ِ خام (پیش‌فرضِ متریال) در: " + "، ".join(raw)
    + " — از appTypography استفاده کنید",
)

# ── ۳) هر دو تم از مشترک بخوانند ─────────────────────────────────
for rel in ("app/src/main/java/com/afghanjama/ui/theme/Theme.kt",
            "desktop/src/main/kotlin/com/afghanjama/desktop/Theme.kt"):
    p = _src.ROOTS[0].parents[3] / rel
    if not p.exists():
        fails.append(f"{rel} پیدا نشد")
        continue
    t = p.read_text(encoding="utf-8")
    check("appTypography(" in t, f"{rel} از مقیاسِ مشترک نمی‌خواند")

# ── ۴) ارتفاعِ خط برای فارسی ─────────────────────────────────────
#
# خطِ فارسی زیر-خط دارد (ج، ح، ر، ی) و اعرابِ گاه‌به‌گاه. با ارتفاعِ
# خطِ لاتین سطرها به هم می‌چسبند. نسبتِ ۱٫۵ حدِ پایین است.
for role in ("bodyLarge", "bodyMedium", "bodySmall"):
    m = re.search(
        rf"{role} = TextStyle\([^)]*fontSize = (\d+)\.sp[^)]*lineHeight = (\d+)\.sp",
        SRC,
    )
    check(m is not None, f"{role} ارتفاعِ خط ندارد — برای فارسی لازم است")
    if m:
        size, height = int(m.group(1)), int(m.group(2))
        check(
            height >= size * 1.5,
            f"{role}: ارتفاعِ خطِ {height} برای اندازهٔ {size} کم است "
            f"(کفِ فارسی {size * 1.5:.0f})",
        )

if fails:
    print(f"✗ {len(fails)} مشکل در مقیاسِ قلم:")
    for f in fails:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ مقیاسِ قلم: هر {len(ROLES)} نقش در :core، هر دو تم از همان می‌خوانند، "
    "و ارتفاعِ خطِ متن برای فارسی کافی است"
)
