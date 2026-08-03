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

# استثنا: حاشیه‌نویسی‌های Room.
#
# `androidx.room` با بقیهٔ androidx فرق دارد: `room-common` یک jarِ
# خالصِ جاواست، نه کتابخانهٔ اندروید. جدولِ داده‌ها با همین‌ها توصیف
# می‌شود و روی ویندوز هم همان توصیف کار می‌کند. موتورِ Room
# (`room-runtime`) اینجا نیست و نباید بیاید.
ALLOWED = ("import androidx.room.",)

# ...ولی نه هر چیزی زیرِ androidx.room.
#
# `room-common` فقط حاشیه‌نویسی است؛ این‌ها در `room-runtime` هستند که
# موتور است و اندروید می‌خواهد. اگر یکی از این‌ها وارد :core شود، مرز
# بی‌سروصدا شکسته و همان روز فرقی با نبودنِ مرز ندارد.
RUNTIME_ONLY = (
    "import androidx.room.Room\n",
    "import androidx.room.Room.",
    "import androidx.room.RoomDatabase",
    "import androidx.room.RoomWarnings",
    "import androidx.room.migration",
    "import androidx.room.testing",
    "import androidx.room.util",
)

bad = []
files = sorted(CORE.rglob("*.kt"))
for p in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        runtime = line.startswith(RUNTIME_ONLY) or line.rstrip() in (
            "import androidx.room.Room",
        )
        if runtime or (line.startswith(BANNED) and not line.startswith(ALLOWED)):
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

# ---- نگهبانِ خودِ بررسی‌ها ----
#
# هیچ بررسی‌ای نباید مسیرِ ماژول را در خودش سفت کند. با دو ماژول شدنِ
# پروژه، `extcheck` دقیقاً همین را داشت و بی‌سروصدا از ۹ تابع به ۱ تابع
# افتاد — سبز ماند در حالی که تقریباً هیچ نمی‌دید. بدترین حالتِ ممکن
# برای یک بررسی.
checks = pathlib.Path(__file__).resolve().parent
hard = []
for p in sorted(checks.glob("*.py")):
    if p.name in ("_src.py", "purecheck.py"):
        continue
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if "app/src/main/java" in line or "core/src/main/kotlin" in line:
            hard.append((p.name, i, line.strip()))

if hard:
    print(f"\n✗ {len(hard)} بررسی مسیرِ ماژول را در خودش سفت کرده")
    for f, i, line in hard:
        print(f"  {f}:{i}  {line[:78]}")
    print("\n  مسیرها باید از _src بیایند، وگرنه ماژولِ تازه از دیدشان می‌افتد.")
    sys.exit(1)

print(f"✓ هیچ بررسی‌ای مسیرِ ماژول را سفت نکرده")
