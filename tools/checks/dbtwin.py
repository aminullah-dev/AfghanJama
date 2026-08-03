#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دو `@Database` باید دقیقاً یک اسکیما را توصیف کنند.

از وقتی ویندوز دفترِ خودش را باز می‌کند، دو اعلانِ `@Database` در پروژه
هست: یکی اندروید (`AppDatabase`) و یکی ویندوز (`DesktopDatabase`).

**چرا دوتاست:** `Migrations.kt` هزار خط است و هر ۴۲ مهاجرتش روی
`SupportSQLiteDatabase` — API فقط-اندرویدی — نوشته شده. یکی کردنِ
`@Database` یعنی بازنویسیِ همهٔ آن‌ها، یعنی دست بردن در کدی که دادهٔ
واقعیِ کارگاه را ارتقا می‌دهد.

**خطری که این دوتایی می‌سازد:** اگر فردا کسی موجودیتی به یکی اضافه کند
و به آن یکی نه، هیچ چیز نمی‌شکند — تا روزی که فایلِ دیتابیس از گوشی به
پی‌سی برود و باز نشود. یا بدتر: باز شود و جدولِ غایب بی‌سروصدا خالی
بماند.

پس اینجا وارسی می‌شود که فهرستِ موجودیت‌ها و عددِ نسخه یکی باشند.
موجودیت‌ها و `DB_VERSION` هر دو در `:core`اند، پس یکی‌بودن **شدنی** است؛
این بررسی فقط نمی‌گذارد از هم بیفتند.
"""
import pathlib
import re
import sys

import _src

ANDROID = _src.find("data/AppDatabase.kt")
DESKTOP = _src.REPO / "desktop/src/main/kotlin/com/afghanjama/desktop/data/DesktopDatabase.kt"

if not DESKTOP.exists():
    print(f"✗ {DESKTOP.name} نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)


def parse(path):
    """فهرستِ موجودیت‌ها و عددِ نسخه از یک اعلانِ `@Database`."""
    t = path.read_text(encoding="utf-8")
    m = re.search(r"@Database\s*\(\s*entities\s*=\s*\[(.*?)\]", t, re.S)
    if not m:
        sys.exit(f"✗ در {path.name} اعلانِ @Database پیدا نشد")
    ents = [e.strip().replace("::class", "") for e in m.group(1).split(",") if e.strip()]
    ver = re.search(r"version\s*=\s*(\w+)", t)
    conv = re.search(r"@TypeConverters\(\s*(\w+)::class", t)
    return ents, (ver.group(1) if ver else None), (conv.group(1) if conv else None)


a_ents, a_ver, a_conv = parse(ANDROID)
d_ents, d_ver, d_conv = parse(DESKTOP)

if not a_ents or not d_ents:
    print("✗ یکی از اعلان‌ها هیچ موجودیتی نداشت — بررسی پوچ بود")
    sys.exit(1)

bad = False

only_a = [e for e in a_ents if e not in d_ents]
only_d = [e for e in d_ents if e not in a_ents]
if only_a or only_d:
    bad = True
    print(f"✗ فهرستِ موجودیت‌ها یکی نیست ({len(a_ents)} اندروید، {len(d_ents)} ویندوز)")
    for e in only_a:
        print(f"  فقط در اندروید: {e}")
    for e in only_d:
        print(f"  فقط در ویندوز:  {e}")

# نسخه باید از یک ثابت بیاید، نه عددِ دستی در هر طرف.
if a_ver != d_ver:
    bad = True
    print(f"✗ عددِ نسخه یکی نیست: اندروید «{a_ver}» ولی ویندوز «{d_ver}»")
elif a_ver != "DB_VERSION":
    bad = True
    print(f"✗ نسخه باید ثابتِ مشترکِ DB_VERSION باشد، نه «{a_ver}»")

if a_conv != d_conv:
    bad = True
    print(f"✗ تبدیل‌گرها یکی نیستند: اندروید «{a_conv}» ولی ویندوز «{d_conv}»")

if bad:
    print("\n  موجودیت‌ها و DB_VERSION هر دو در :core هستند، پس یکی‌بودن شدنی است.")
    print("  اگر از هم بیفتند، فایلِ دیتابیس بینِ گوشی و پی‌سی باز نمی‌شود.")
    sys.exit(1)

print(f"✓ دو @Database یکی‌اند — {len(a_ents)} موجودیت، نسخه از {a_ver}")
