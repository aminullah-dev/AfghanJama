#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر `@Database` باید دقیقاً یک اسکیما را توصیف کند.

از وقتی هر سکو دفترِ خودش را باز می‌کند، سه اعلانِ `@Database` در پروژه
هست: اندروید (`AppDatabase`)، ویندوز (`DesktopDatabase`) و آیفون
(`IosDatabase`).

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
import json
import pathlib
import re
import sys

import _src

#: هر اعلانِ `@Database` با نامی که در پیام‌ها دیده می‌شود.
#:
#: اولی مرجع است و بقیه با آن سنجیده می‌شوند — نه چون مهم‌تر است، بلکه
#: چون مقایسه باید یک نقطهٔ ثابت داشته باشد.
DECLS = [
    ("اندروید", _src.find("data/AppDatabase.kt")),
    ("ویندوز", _src.REPO
        / "desktop/src/main/kotlin/com/afghanjama/desktop/data/DesktopDatabase.kt"),
    ("آیفون", _src.CORE / "com/afghanjama/ios/data/IosDatabase.kt"),
]

for name, path in DECLS:
    if not path.exists():
        print(f"✗ اعلانِ {name} ({path.name}) نیست — بررسی پوچ بود، مسیر را ببینید")
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


parsed = [(name, *parse(path)) for name, path in DECLS]

if any(not ents for _, ents, _, _ in parsed):
    print("✗ یکی از اعلان‌ها هیچ موجودیتی نداشت — بررسی پوچ بود")
    sys.exit(1)

bad = False
ref_name, ref_ents, ref_ver, ref_conv = parsed[0]

for name, ents, ver, conv in parsed[1:]:
    only_ref = [e for e in ref_ents if e not in ents]
    only_this = [e for e in ents if e not in ref_ents]
    if only_ref or only_this:
        bad = True
        print(f"✗ موجودیت‌های {ref_name} و {name} یکی نیستند "
              f"({len(ref_ents)} و {len(ents)})")
        for e in only_ref:
            print(f"  فقط در {ref_name}: {e}")
        for e in only_this:
            print(f"  فقط در {name}: {e}")
    if ver != ref_ver:
        bad = True
        print(f"✗ عددِ نسخه یکی نیست: {ref_name} «{ref_ver}» ولی {name} «{ver}»")
    if conv != ref_conv:
        bad = True
        print(f"✗ تبدیل‌گرها یکی نیستند: {ref_name} «{ref_conv}» ولی {name} «{conv}»")

# نسخه باید از یک ثابت بیاید، نه عددِ دستی در هر طرف.
if ref_ver != "DB_VERSION":
    bad = True
    print(f"✗ نسخه باید ثابتِ مشترکِ DB_VERSION باشد، نه «{ref_ver}»")

# ── و مهم‌تر از اعلان: خودِ طرحِ صادرشده ──
#
# **چرا این بند اضافه شد.** مقایسهٔ بالا روی متنِ `@Database` است و
# چیزی را می‌گیرد که آدم می‌بیند: فهرست موجودیت‌ها. ولی طرحِ واقعی از
# خودِ موجودیت‌ها ساخته می‌شود و می‌تواند جای دیگری از هم بیفتد —
# نمایه‌ای که فقط یک سکو دارد، یا ستونی که در یکی nullable است.
#
# هر سه سکو طرحشان را در `schemas/` صادر می‌کنند و آن فایل‌ها در گیت
# هستند. پس مقایسهٔ `createSql`ِ هر جدول ارزان است و **چیزی را می‌گیرد
# که خواندنِ کد نمی‌گیرد**.
schemas = sorted(_src.REPO.glob(f"*/schemas/*/*.json"))
by_ver = {}
for f in schemas:
    by_ver.setdefault(f.stem, []).append(f)

newest = max(by_ver, key=lambda v: int(v)) if by_ver else None
if newest is None:
    bad = True
    print("✗ هیچ طرحِ صادرشده‌ای پیدا نشد — بررسی پوچ بود")
elif len(by_ver[newest]) < len(DECLS):
    bad = True
    have = sorted(f.parts[-4] for f in by_ver[newest])
    print(f"✗ طرحِ نسخهٔ {newest} فقط برای {have} صادر شده، نه هر {len(DECLS)} سکو")
else:
    ref_file = by_ver[newest][0]
    ref_tables = {
        t["tableName"]: t["createSql"]
        for t in json.loads(ref_file.read_text(encoding="utf-8"))["database"]["entities"]
    }
    for f in by_ver[newest][1:]:
        tables = {
            t["tableName"]: t["createSql"]
            for t in json.loads(f.read_text(encoding="utf-8"))["database"]["entities"]
        }
        if tables != ref_tables:
            bad = True
            print(f"✗ طرحِ {f.parts[-4]} با {ref_file.parts[-4]} یکی نیست")
            for t in sorted(set(ref_tables) ^ set(tables)):
                print(f"  جدولِ {t} فقط در یکی هست")
            for t in sorted(set(ref_tables) & set(tables)):
                if ref_tables[t] != tables[t]:
                    print(f"  SQLِ جدولِ {t} فرق دارد")

if bad:
    print("\n  موجودیت‌ها و DB_VERSION هر دو در :core هستند، پس یکی‌بودن شدنی است.")
    print("  اگر از هم بیفتند، فایلِ دیتابیس بینِ گوشی و پی‌سی باز نمی‌شود.")
    sys.exit(1)

print(f"✓ هر {len(DECLS)} اعلانِ @Database یکی‌اند — {len(ref_ents)} موجودیت، "
      f"نسخه از {ref_ver}؛ و طرحِ صادرشدهٔ نسخهٔ {newest} در هر سه یکسان است")
