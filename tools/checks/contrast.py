#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""متن روی هر سطحی که واقعاً رویش می‌نشیند، خوانده شود.

**چرا این بررسی هست.** رنگ تنها جای طراحی است که «قشنگ به نظر رسیدن» و
«کار کردن» می‌توانند کاملاً از هم جدا بیفتند. رمپِ اولِ مسِ این پروژه
روی صفحه خوش‌رنگ بود، ولی وقتی نسبتِ کنتراستش اندازه گرفته شد:

    OnCoral روی CoralDeep      = ۲٫۸۷   (حد: ۴٫۵)
    OnCoralMuted روی Coral     = ۲٫۶۷   (حد: ۳٫۰)
    OnCoralMuted روی CoralDeep = ۱٫۵۷   (حد: ۳٫۰)
    outlineVariant روی surface   = ۱٫۴۰   (حد: ۱٫۵)

یعنی عددِ موجودیِ نقد روی لبهٔ تیرهٔ گرادیان سخت خوانده می‌شد و
جداکنندهٔ کارت‌ها در حالتِ تاریک عملاً نامرئی بود. هیچ‌کدام کامپایل را
نمی‌شکند و هیچ آزمونی هم نمی‌گیردشان؛ فقط کاربری که زیرِ نورِ کارگاه
به صفحه نگاه می‌کند می‌فهمد چیزی درست نیست — و معمولاً نمی‌تواند
بگوید چه.

معیار WCAG 2.1 است: **۴٫۵** برای متنِ معمولی، **۳٫۰** برای متنِ فرعی و
عناصرِ بزرگ، و برای جداکننده حدِ نرم‌ترِ **۱٫۵** (جداکننده متن نیست،
فقط باید دیده شود).

رنگ‌ها از خودِ کاتلین خوانده می‌شوند، نه از یک نسخهٔ دستیِ اینجا —
وگرنه اولین باری که کسی پالت را عوض کند، این بررسی سبزِ دروغ می‌دهد.
"""
import re
import sys

import _src

BRAND = _src.CORE / "com/afghanjama/ui/theme/Brand.kt"
PALETTE = _src.CORE / "com/afghanjama/ui/theme/Palette.kt"

AA_TEXT = 4.5      # متنِ معمولی
AA_LARGE = 3.0     # متنِ فرعی و عناصرِ بزرگ
VISIBLE = 1.5      # جداکننده — فقط باید دیده شود


def srgb(c: int) -> float:
    x = c / 255
    return x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4


def luminance(rgb: int) -> float:
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)


def ratio(a: int, b: int) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def colors_of(path, pattern):
    """نام → RGB، از روی `Color(0xFFRRGGBB)`های همان فایل."""
    if not path.exists():
        print(f"✗ {path.name} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)
    src = path.read_text(encoding="utf-8")
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    out = {}
    for m in re.finditer(pattern, src):
        out[m.group(1)] = int(m.group(2), 16) & 0xFFFFFF
    return out


brand = colors_of(BRAND, r"val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)")
# پالت دو طرح دارد؛ فقط بلوکِ تاریک لازم است
pal_src = PALETTE.read_text(encoding="utf-8")
dark_block = pal_src[pal_src.index("val DarkColors"):]
dark = {
    m.group(1): int(m.group(2), 16) & 0xFFFFFF
    for m in re.finditer(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", dark_block)
}
light_block = pal_src[pal_src.index("val LightColors"):pal_src.index("val DarkColors")]
light = {
    m.group(1): int(m.group(2), 16) & 0xFFFFFF
    for m in re.finditer(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", light_block)
}

if not brand or not dark or not light:
    print("✗ رنگی خوانده نشد — قالبِ فایل عوض شده و بررسی دیگر چیزی نمی‌سنجد.")
    sys.exit(1)

#: (متن، زمینه، حد، توضیحِ اینکه کجای اپ است)
#
# فقط جفت‌هایی که **واقعاً** روی هم می‌نشینند. جفتِ ساختگی یعنی
# بررسی‌ای که یا بی‌جهت قرمز می‌شود یا اعتماد را از بین می‌برد.
PAIRS = []

# ── مس: هر چهار پله، چون گرادیان از همه‌شان می‌گذرد ──────────────
for stop in ("CoralDeep", "Coral", "CoralSheen", "CoralLight"):
    PAIRS.append((("brand", "OnCoral"), ("brand", stop), AA_TEXT,
                  f"عددِ روی کارتِ مسی، جایی که گرادیان {stop} است"))
    PAIRS.append((("brand", "OnCoralMuted"), ("brand", stop), AA_LARGE,
                  f"برچسبِ روی کارتِ مسی، جایی که گرادیان {stop} است"))

# ── زمرد: صفحهٔ ورود و قفل ───────────────────────────────────────
for stop in ("PetrolDeep", "Petrol", "PetrolLight"):
    PAIRS.append((("brand", "OnPetrol"), ("brand", stop), AA_TEXT,
                  f"عنوانِ صفحهٔ ورود روی {stop}"))
    PAIRS.append((("brand", "OnPetrolMuted"), ("brand", stop), AA_LARGE,
                  f"زیرنویسِ صفحهٔ ورود روی {stop}"))

# ── سطح‌های هر دو طرح ────────────────────────────────────────────
for scheme in ("dark", "light"):
    for surf in ("background", "surface", "surfaceVariant",
                 "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest"):
        PAIRS.append(((scheme, "onSurface"), (scheme, surf), AA_TEXT,
                      f"متنِ اصلی روی {surf} در طرحِ {scheme}"))
        PAIRS.append(((scheme, "onSurfaceVariant"), (scheme, surf), AA_LARGE,
                      f"متنِ فرعی روی {surf} در طرحِ {scheme}"))
    for role in ("primary", "secondary", "error", "outline"):
        PAIRS.append(((scheme, role), (scheme, "surface"), AA_LARGE,
                      f"{role} روی surface در طرحِ {scheme}"))
    for pair in (("onPrimaryContainer", "primaryContainer"),
                 ("onSecondaryContainer", "secondaryContainer"),
                 ("onTertiaryContainer", "tertiaryContainer"),
                 ("onErrorContainer", "errorContainer"),
                 ("onPrimary", "primary"),
                 ("onSecondary", "secondary"),
                 ("onError", "error")):
        PAIRS.append(((scheme, pair[0]), (scheme, pair[1]), AA_TEXT,
                      f"{pair[0]} روی {pair[1]} در طرحِ {scheme}"))
    PAIRS.append(((scheme, "outlineVariant"), (scheme, "surface"), VISIBLE,
                  f"جداکنندهٔ کارت‌ها در طرحِ {scheme}"))

BOOK = {"brand": brand, "dark": dark, "light": light}

fail = []
checked = 0
for (fg_src, fg), (bg_src, bg), need, where in PAIRS:
    a, b = BOOK[fg_src].get(fg), BOOK[bg_src].get(bg)
    if a is None or b is None:
        fail.append(f"رنگِ «{fg if a is None else bg}» پیدا نشد — {where}")
        continue
    checked += 1
    r = ratio(a, b)
    if r < need:
        fail.append(
            f"{where}: نسبت {r:.2f} — حد {need}. "
            f"({fg}=#{a:06X} روی {bg}=#{b:06X})"
        )

if fail:
    print(f"✗ {len(fail)} جفتِ رنگ خوانا نیست")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ کنتراست: هر {checked} جفتِ رنگ از حدِ WCAG می‌گذرد")
