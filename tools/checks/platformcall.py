#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`:core` نباید به تابعی وصل شود که کلاسش بینِ دو سکو فرق می‌کند.

**چرا این بررسی هست — گران‌ترین اشکالِ این پروژه:**

اپ روی گوشیِ کارگاه سرِ باز کردنِ صفحهٔ سفارش می‌ترکید:

    java.lang.NoClassDefFoundError: Failed resolution of:
    Landroidx/compose/material3/SkikoMenu_skikoKt;
        at ProductionOrderScreen.kt:161

فرضِ کلِ فازِ ۴.۵ این بود که `androidx.compose.*` روی هر دو سکو نامِ
یکسان دارد. برای صدها تابع درست بود؛ برای سه‌تا نبود. تابعِ سطحِ فایل در
جاوا به کلاسی به نامِ همان **فایل** تبدیل می‌شود، و `DropdownMenu` روی
دسکتاپ در `SkikoMenu.skiko.kt` است و روی اندروید در
`AndroidMenu.android.kt`. نامِ کاتلینی یکی، کلاسِ بایت‌کد دوتا.

نتیجه: کامپایل سبز، APK ساخته‌شده، و کرش فقط **وقتی آن صفحه باز می‌شد**.
نه ۲۹ بررسی چیزی گفتند، نه آزمونِ شبیه‌ساز — چون هیچ‌کدام آن صفحه را باز
نمی‌کردند.

**دو حالتِ کار:**

  • *بایت‌کد* (دقیق): اگر `:core` کامپایل شده باشد، خودِ فایل‌های
    `.class` خوانده می‌شوند و هر ارجاع به کلاسِ `*_skikoKt` و برادرانش
    گرفته می‌شود. این حالت به نسخهٔ Compose وابسته نیست و چیزی از
    قلم نمی‌اندازد.

  • *متن* (سریع): پیش از Gradle، ایمپورت‌های `androidx.compose.*` هر
    فایلِ `:core` با فهرستِ `platform_symbols.txt` سنجیده می‌شود.

حالتی که اجرا شده **صریح چاپ می‌شود**. بررسی‌ای که نگوید چه‌قدر مطمئن
است، از بررسی‌ای که هیچ نمی‌گوید بدتر است.

`--require-bytecode` حالتِ متنی را قبول نمی‌کند؛ CI بعد از ساختِ
`:core` با همین سوییچ صدایش می‌زند.
"""
import pathlib
import re
import sys

import _src

SUFFIXES = ("_skikoKt", "_desktopKt", "_jvmKt", "_awtKt", "_skikoMainKt")
CLASSES = _src.REPO / "core/build/classes/kotlin/main"
LIST = pathlib.Path(__file__).resolve().parent / "platform_symbols.txt"

require_bytecode = "--require-bytecode" in sys.argv


def load_symbols():
    out = {}
    for line in LIST.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, _, cls = line.partition(" ")
        out[name] = cls
    return out


def check_bytecode():
    """دقیق: ارجاعِ واقعی در فایل‌های `.class`."""
    files = sorted(CLASSES.rglob("*.class"))
    if not files:
        return None
    pat = re.compile(
        rb"androidx/compose/[A-Za-z0-9/_]+/([A-Za-z0-9_]+(?:"
        + b"|".join(s.encode() for s in SUFFIXES)
        + rb"))"
    )
    bad = {}
    for p in files:
        for m in set(pat.findall(p.read_bytes())):
            bad.setdefault(m.decode(), set()).add(p.stem.split("$")[0])
    return files, bad


def check_source(symbols):
    """سریع: ایمپورتِ `androidx.compose.*`ی که در فهرست است."""
    files = sorted(_src.CORE.rglob("*.kt"))
    if not files:
        print("✗ هیچ فایلی در :core نیست — بررسی پوچ بود، مسیر را ببینید")
        sys.exit(1)

    bad, wildcard = [], []
    imp = re.compile(r"^import\s+(androidx\.compose\.[\w.]+?)\.(\w+)\s*$")
    star = re.compile(r"^import\s+androidx\.compose\.[\w.]+\.\*\s*$")
    for p in files:
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if star.match(line):
                # ایمپورتِ ستاره‌دار این بررسی را دور می‌زند: نامی که
                # می‌آید در متن دیده نمی‌شود.
                wildcard.append((p.name, i, line.strip()))
                continue
            m = imp.match(line)
            if m and m.group(2) in symbols:
                bad.append((p.name, i, m.group(2), symbols[m.group(2)]))
    return files, bad, wildcard


symbols = load_symbols()
if not symbols:
    print("✗ platform_symbols.txt خالی است — بررسی پوچ می‌شد.")
    sys.exit(1)

result = check_bytecode()

if result is not None:
    files, bad = result
    if bad:
        print(f"✗ {len(bad)} کلاسِ سکو-ویژه در بایت‌کدِ :core")
        for cls, users in sorted(bad.items()):
            print(f"  {cls}")
            for u in sorted(users)[:6]:
                print(f"      از {u}")
        print("\n  این‌ها روی اندروید وجود ندارند و اپ سرِ باز کردنِ آن")
        print("  صفحه با NoClassDefFoundError می‌ترکد. راهش مرزِ سکوست:")
        print("  `core/ui/platform/Widgets.kt` را ببینید.")
        sys.exit(1)
    print(f"✓ بایت‌کدِ :core — {len(files)} کلاس، هیچ ارجاعِ سکو-ویژه‌ای نیست")
    sys.exit(0)

if require_bytecode:
    print(f"✗ {CLASSES} نیست — :core کامپایل نشده و حالتِ دقیق اجرا نشد.")
    print("  با --require-bytecode سکوت پذیرفته نیست.")
    sys.exit(1)

files, bad, wildcard = check_source(symbols)
if bad or wildcard:
    print(f"✗ {len(bad) + len(wildcard)} ایمپورتِ خطرناک در :core")
    for f, i, name, cls in bad:
        print(f"  {f}:{i}  {name}  ← روی دسکتاپ در {cls}")
    for f, i, line in wildcard:
        print(f"  {f}:{i}  {line}  ← ایمپورتِ ستاره‌دار، بررسی را کور می‌کند")
    print("\n  نامِ کاتلینی یکی است ولی کلاسِ بایت‌کد بینِ دو سکو فرق دارد.")
    print("  از مرزِ `core/ui/platform/Widgets.kt` رد شوید.")
    sys.exit(1)

print(
    f"✓ متنِ :core — {len(files)} فایل، هیچ‌کدام از {len(symbols)} نمادِ "
    f"سکو-ویژه ایمپورت نشده (بایت‌کد موجود نبود)"
)
