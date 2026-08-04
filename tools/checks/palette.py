#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""رنگِ خام در `:desktop` — یعنی پالت دارد دو تا می‌شود.

**چرا لازم شد:** پنجرهٔ ویندوز چهار رنگ را از تمِ اندروید کپی کرده بود و
`lightColorScheme` را فقط با همان چهارتا ساخته بود. ولی صفحه‌های مشترک
**۱۸ نقشِ رنگ** صدا می‌زنند — `onSurfaceVariant` ۱۵۸ بار،
`outlineVariant` ۷۷، `error` ۵۸. آن چهارده نقشِ دیگر از پالتِ **پیش‌فرضِ
بنفشِ متریال** می‌آمدند.

یعنی همان صفحه، با همان کد، روی گوشی سبز-برنزی بود و روی پی‌سی
بنفش-خاکستری. **کامپایل درست بود و هیچ بررسی‌ای چیزی نمی‌گفت** — تنها
راهِ فهمیدنش باز کردنِ پنجره روی ویندوز بود، کاری که هنوز انجام نشده.

حالا پالت در `:core` است و هر دو سکو از همان می‌خوانند. این بررسی جلوی
برگشتش را می‌گیرد: هر `Color(0x…)`ی در `:desktop` یعنی کسی دوباره رنگی
را دستی نوشته.

**چرا فقط `:desktop`:** رنگ‌های ثابتِ تابلوی کارگاه در `:core`اند و
عمداً از تم نمی‌آیند (تابلو همیشه تیره است، چه تمِ روشن چه تاریک). آنجا
رنگِ خام درست است؛ اینجا نه.
"""
import re
import sys

import _src

LITERAL = re.compile(r"Color\(\s*0x[0-9A-Fa-f]{6,8}")


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


files = sorted(_src.DESKTOP.rglob("*.kt"))
if not files:
    print("✗ هیچ فایلی در :desktop نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

bad = []
for p in files:
    for i, line in enumerate(strip(p.read_text(encoding="utf-8")).splitlines(), 1):
        if LITERAL.search(line):
            bad.append((p.relative_to(_src.DESKTOP), i, line.strip()[:60]))

if bad:
    print(f"✗ {len(bad)} رنگِ خام در :desktop")
    for f, i, line in bad:
        print(f"  {f}:{i}  {line}")
    print("\n  پالت در `:core/ui/theme/Palette.kt` است و هر دو سکو باید از")
    print("  همان بخوانند. رنگِ دستی یعنی روزی گوشی و پی‌سی فرق می‌کنند.")
    sys.exit(1)

print(f"✓ {len(files)} فایلِ :desktop — هیچ رنگی دستی نوشته نشده")
