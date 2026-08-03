#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ماژولِ :core باید بی‌اندروید بماند.

این مرز چیزی است که نسخهٔ ویندوز رویش بنا می‌شود: هر خطی که در `:core`
است روی گوشی و روی پی‌سی یکسان اجرا می‌شود. کافی است کسی یک
`import android.content.Context` اضافه کند تا آن ضمانت از بین برود.

کامپایلر هم همین را می‌گیرد (اندروید اصلاً در classpathِ این ماژول
نیست)، ولی کامپایلر فقط در CI اجرا می‌شود و ده دقیقه طول می‌کشد. این
بررسی در یک ثانیه همان را می‌گوید.
"""
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
CORE = REPO / "core/src/main/kotlin"

BANNED = ("import android.", "import androidx.")

bad = []
files = sorted(CORE.rglob("*.kt"))
for p in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if line.startswith(BANNED):
            bad.append((p.relative_to(CORE), i, line.strip()))

if not files:
    print("✗ هیچ فایلی در :core نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

if bad:
    print(f"✗ {len(bad)} ایمپورتِ اندرویدی در :core")
    for f, i, line in bad:
        print(f"  {f}:{i}  {line}")
    print("\n  :core باید روی ویندوز هم کامپایل شود. این خط آنجا وجود ندارد.")
    sys.exit(1)

print(f"✓ {len(files)} فایلِ :core — هیچ‌کدام به اندروید وابسته نیست")
