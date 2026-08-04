#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دو دامِ عکس که هر دو واقعاً افتادند و هیچ‌کدام کامپایل را نشکستند.

**دامِ ۱ — `inJustDecodeBounds` هیچ‌وقت بیت‌مپ نمی‌دهد.**

کدِ خواندنِ ابعادِ عکس چنین بود:

    read()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

ولی `decodeStream` وقتی `inJustDecodeBounds` روشن است **طبقِ سند همیشه
`null` برمی‌گرداند**؛ نتیجه در خودِ `opts` می‌نشیند. پس آن `?: return
null` همیشه شلیک می‌کرد و تابع در هر حالتی `null` می‌داد.

دنباله‌اش: انتخابِ عکس از گالری همیشه «عکس کپی نشد» می‌داد و هیچ عکسی
هم نمایش داده نمی‌شد. از روزی که این قابلیت اضافه شد تا امروز، **هیچ
عکسی هرگز کار نکرد**. از نظرِ نوع‌ها هیچ چیزی غلط نبود، پس نه کامپایلر
چیزی گفت نه هیچ بررسی‌ای.

**دامِ ۲ — جاروی عکس‌های بی‌صاحب، لوگو را هم می‌بُرد.**

جاروی راه‌اندازی هرچه را در `livePhotoFileNames()` نبود پاک می‌کرد، و آن
فهرست از **جدول‌ها** می‌آید. لوگوی کارگاه در `SharedPreferences` است،
پس هر بار که اپ باز می‌شد پاک می‌شد. از دیدِ کاربر: لوگو انتخاب می‌شد،
دیده هم می‌شد، و دفعهٔ بعد نبود.

پس: جارو فقط از `PhotoStore.sweep` رد شود، و هر جا که صدایش می‌زند باید
نگه‌دارنده‌های بیرونِ دیتابیس را هم بشناسد.
"""
import re
import sys

import _src

# نامِ هر چیزی که نامِ عکسی را **بیرونِ دیتابیس** نگه می‌دارد.
# هر جای تازه‌ای از این دست باید اینجا و در `App.keptPhotoNames` اضافه شود.
NON_DB_HOLDERS = ["CompanyPrefs.logo"]


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


files = sorted(_src.APP.rglob("*.kt"))
if not files:
    print("✗ هیچ فایلی در :app نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

fail = []
saw_bounds = False
saw_sweep_def = False
sweep_calls = []

for p in files:
    raw = p.read_text(encoding="utf-8")
    src = strip(raw)
    flat = re.sub(r"\s+", " ", src)

    # ── دامِ ۱ ────────────────────────────────────────────────────
    if "inJustDecodeBounds" in src:
        saw_bounds = True
        # خروجیِ `decodeStream` نباید نشانهٔ موفقیت باشد: نه با `?:`، نه
        # با مقایسه با null.
        if re.search(r"decodeStream\s*\([^)]*\)\s*\}?\s*(\?:|[!=]= *null)", flat):
            fail.append(
                f"{p.name}: خروجیِ decodeStream نشانهٔ موفقیت گرفته شده، ولی با "
                f"inJustDecodeBounds همیشه null است. نتیجه در opts.outWidth/"
                f"outHeight است."
            )

    # ── دامِ ۲ ────────────────────────────────────────────────────
    if "fun sweep(" in src and "PhotoStore" in raw:
        saw_sweep_def = True
    # پاک کردنِ دسته‌جمعیِ فایل بیرون از `sweep` ممنوع است.
    #
    # نسخهٔ اولِ این بند پنجره را از **اولین** `listFiles` فایل می‌گرفت،
    # نه از موردی که دارد بررسی می‌شود — یعنی برای هر تکرار یک جواب
    # می‌داد. حالا از خودِ همان مورد شمرده می‌شود.
    if "fun sweep(" not in src:
        for m in re.finditer(r"listFiles\s*\(\s*\)", src):
            if "delete()" in src[m.end() : m.end() + 300]:
                fail.append(
                    f"{p.name}: فایل‌ها دسته‌جمعی پاک می‌شوند بیرون از "
                    f"PhotoStore.sweep — جارو باید یک جا بماند."
                )
                break

    if "PhotoStore.sweep(" in src:
        sweep_calls.append((p.name, src))

if not saw_bounds:
    print("✗ هیچ‌جا inJustDecodeBounds نیست — بررسی پوچ می‌شد.")
    sys.exit(1)
if not saw_sweep_def:
    print("✗ PhotoStore.sweep پیدا نشد — جارو جای دیگری رفته است.")
    sys.exit(1)
if not sweep_calls:
    fail.append("هیچ‌کس PhotoStore.sweep را صدا نمی‌زند — عکسِ بی‌صاحب می‌مانَد.")
elif not any(name == "App.kt" for name, _ in sweep_calls):
    # «دستِ‌کم یکی صدایش می‌زند» کافی نبود: با برداشتنِ جاروی راه‌اندازی،
    # جاروی «پاک کردنِ داده‌ها» تنها می‌مانْد و بررسی سبز. ولی آن یکی
    # فقط وقتی اجرا می‌شود که کاربر داده‌ها را پاک کند؛ جاروی واقعی
    # همان راه‌اندازی است.
    fail.append(
        "App.kt جارو را صدا نمی‌زند — جاروی راه‌اندازی برداشته شده و "
        "عکسِ بی‌صاحب برای همیشه می‌مانَد."
    )

for name, src in sweep_calls:
    for holder in NON_DB_HOLDERS:
        if holder not in src:
            fail.append(
                f"{name}: جارو صدا زده می‌شود ولی «{holder}» در این فایل نیست. "
                f"آن نامِ عکسی است که بیرونِ دیتابیس نگه داشته می‌شود و "
                f"بی آن، جارو پاکش می‌کند."
            )

if fail:
    print(f"✗ {len(fail)} مشکل در مسیرِ عکس")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ عکس: ابعاد از opts خوانده می‌شود، و جارو در {len(sweep_calls)} جا "
    f"نگه‌دارنده‌های بیرونِ دیتابیس را می‌شناسد"
)
