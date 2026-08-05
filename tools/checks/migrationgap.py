#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""زنجیرهٔ مهاجرت نباید پاره باشد — روی هیچ‌کدام از دو سکو.

**چرا این بررسی هست.** `DB_VERSION` یک عدد در `:core` است و بالا بردنش
یک کاراکتر کار دارد. ولی هر بار که بالا برود، دو چیز باید همراهش بیاید
و هیچ‌کدام را کامپایلر نمی‌گوید:

1. **ویندوز** باید گامِ تازه را در `SCHEMA_STEPS` داشته باشد. اگر نداشته
   باشد، Room نمی‌تواند دفترِ موجود را بالا ببرد و چون روی دسکتاپ
   `fallbackToDestructiveMigration` عمداً نیست، **دفترِ کارگاه باز
   نمی‌شود**. کار می‌خوابد تا کسی نسخهٔ تازه بدهد.

2. **اندروید** باید مهاجرتِ تازه را داشته باشد. اینجا خرابی بدتر است:
   `buildAppDatabase` روی `fallbackToDestructiveMigration` است، یعنی
   Room با ندیدنِ گام **کلِ دفترِ کارگاه را بی هیچ پیامی پاک می‌کند**.

هیچ‌کدام سرِ ساخت دیده نمی‌شوند. هر دو سرِ اجرا و روی دادهٔ واقعی
پیدا می‌شوند — یعنی دیرترین و گران‌ترین جای ممکن.

سه چیز وارسی می‌شود:
  الف) زنجیرهٔ ویندوز: `DESKTOP_BASELINE` تا `DB_VERSION`
  ب) زنجیرهٔ اندروید: ۱۹ تا `DB_VERSION`
  ج) هر `MIGRATION_x_y`ِ نوشته‌شده در `ALL_MIGRATIONS` هم ثبت شده باشد
     — مهاجرتی که نوشته شود و ثبت نشود همان پاک‌شدنِ خاموش را می‌دهد.
"""
import re
import sys

import _src

SCHEMA = _src.CORE / "com/afghanjama/data/SchemaMigrations.kt"
LEGACY = _src.CORE / "com/afghanjama/data/LegacySchemaSteps.kt"
DBSCHEMA = _src.CORE / "com/afghanjama/data/DbSchema.kt"
ANDROID = _src.find("data/Migrations.kt")

for p in (SCHEMA, LEGACY, DBSCHEMA, ANDROID):
    if not p.exists():
        print(f"✗ {p} نیست — بررسی پوچ بود، مسیر را ببینید")
        sys.exit(1)


def strip_comments(text):
    """بی این، عددهای داخلِ توضیح به حساب می‌آیند و بررسی پوچ می‌شود."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


schema_src = strip_comments(SCHEMA.read_text(encoding="utf-8"))
android_src = strip_comments(ANDROID.read_text(encoding="utf-8"))
db_src = strip_comments(DBSCHEMA.read_text(encoding="utf-8"))


def one_int(pattern, text, what):
    m = re.search(pattern, text)
    if not m:
        print(f"✗ {what} پیدا نشد — بررسی پوچ می‌شد")
        sys.exit(1)
    return int(m.group(1))


DB_VERSION = one_int(r"const\s+val\s+DB_VERSION\s*=\s*(\d+)", db_src, "DB_VERSION")
BASELINE = one_int(
    r"const\s+val\s+DESKTOP_BASELINE\s*=\s*(\d+)", schema_src, "DESKTOP_BASELINE"
)

# گام‌های مشترک. خطِ اعلانِ خودِ کلاس («class SchemaStep(val from…») نباید
# به حساب بیاید، وگرنه یک گامِ ساختگی شمرده می‌شود.
STEP = r"(?<!class )SchemaStep\(\s*(?:from\s*=\s*)?(\d+)\s*,\s*(?:to\s*=\s*)?(\d+)"

shared = [(int(a), int(b)) for a, b in re.findall(STEP, schema_src)]

# گام‌های تاریخیِ استخراج‌شده — فقط ویندوز از این‌ها استفاده می‌کند.
legacy_src = strip_comments(LEGACY.read_text(encoding="utf-8"))
legacy = [(int(a), int(b)) for a, b in re.findall(STEP, legacy_src)]

# دو مهاجرتِ دستیِ دسکتاپ (کرسر دارند و SQLِ خالص نمی‌شوند). بی این‌ها
# زنجیره سرِ ۴۱→۴۲ پاره است و پایه نمی‌تواند زیرِ ۴۳ برود.
HAND = _src.REPO / "desktop/src/main/kotlin/com/afghanjama/desktop/data/LegacyHandMigrations.kt"
if not HAND.exists():
    print(f"✗ {HAND.name} نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)
hand_src = strip_comments(HAND.read_text(encoding="utf-8"))
hand = [
    (int(a), int(b))
    for a, b in re.findall(r"object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", hand_src)
]

# آنچه ویندوز واقعاً می‌تواند اجرا کند.
desktop = legacy + hand + shared

# مهاجرت‌های اندروید، از خودِ اعلان‌ها نه از فهرست.
android = [
    (int(a), int(b))
    for a, b in re.findall(r"object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", android_src)
]

problems = []


def chain(steps, start, end, who):
    """آیا با این گام‌ها می‌شود از `start` به `end` رسید؟"""
    have = {}
    for f, t in steps:
        have.setdefault(f, t)
    v = start
    while v < end:
        if v not in have:
            problems.append(
                f"{who}: گامِ {v} → {v + 1} نیست "
                f"(زنجیره از {start} تا {end} باید کامل باشد)"
            )
            return
        v = have[v]


# الف) ویندوز — از پایه تا امروز
chain(desktop, BASELINE, DB_VERSION, "ویندوز (LEGACY_STEPS + دستی + SCHEMA_STEPS)")

# الف-۲) و `DESKTOP_BASELINE` باید **راست** بگوید.
#
# عدد را دست بردن یک کاراکتر کار دارد و هیچ چیز نمی‌گفت اگر دروغ می‌شد:
# پایه‌ای پایین‌تر از واقع یعنی برنامه به کاربر می‌گوید «فایلت را بیاور»
# و بعد سرِ اجرا با خطای خامِ Room می‌ایستد. پس پایینی‌ترین نسخه‌ای که
# واقعاً به `DB_VERSION` می‌رسد حساب می‌شود و با عددِ اعلام‌شده سنجیده.
have_desktop = {}
for f, t in desktop:
    have_desktop.setdefault(f, t)


def reaches(start):
    v = start
    while v < DB_VERSION:
        if v not in have_desktop:
            return False
        v = have_desktop[v]
    return True


true_floor = next((v for v in range(1, DB_VERSION + 1) if reaches(v)), DB_VERSION)
if true_floor != BASELINE:
    problems.append(
        f"DESKTOP_BASELINE={BASELINE} با زنجیرهٔ واقعی نمی‌خواند: "
        f"پایین‌ترین نسخه‌ای که به {DB_VERSION} می‌رسد {true_floor} است"
    )

# ب) اندروید — تاریخی + مشترک
chain(android + shared, 19, DB_VERSION, "اندروید (Migrations.kt + SCHEMA_STEPS)")

# ج) هر مهاجرتِ نوشته‌شده باید ثبت هم شده باشد
declared = set(re.findall(r"val\s+(MIGRATION_\d+_\d+)\s*=", android_src))
# فهرست در `AppDatabase.kt` است نه کنارِ خودِ مهاجرت‌ها — اولین بار
# همین‌جا اشتباه شد و بررسی «فهرست پیدا نشد» داد.
appdb_src = strip_comments(_src.find("data/AppDatabase.kt").read_text(encoding="utf-8"))
m = re.search(r"ALL_MIGRATIONS\s*(?::[^=]+)?=\s*arrayOf\((.*?)\)", appdb_src, re.S)
if m is None:
    problems.append("اندروید: فهرستِ ALL_MIGRATIONS پیدا نشد")
else:
    registered = set(re.findall(r"MIGRATION_\d+_\d+", m.group(1)))
    for name in sorted(declared - registered):
        problems.append(
            f"اندروید: {name} نوشته شده ولی در ALL_MIGRATIONS نیست — "
            f"Room آن را نمی‌بیند و دفتر را پاک می‌کند"
        )

if problems:
    print(f"✗ {len(problems)} پارگی در زنجیرهٔ مهاجرت")
    for p in problems:
        print(f"  {p}")
    print(
        f"\n  DB_VERSION={DB_VERSION}، DESKTOP_BASELINE={BASELINE}، "
        f"{len(android)} مهاجرتِ اندروید، {len(legacy)} استخراجی، {len(hand)} دستی، "
        f"{len(shared)} مشترک."
    )
    print("  گامِ تازه در core/.../SchemaMigrations.kt نوشته می‌شود (یک بار، برای هر دو سکو).")
    sys.exit(1)

print(
    f"✓ زنجیرهٔ مهاجرت کامل است — اندروید ۱۹→{DB_VERSION}، "
    f"ویندوز {BASELINE}→{DB_VERSION} "
    f"({len(legacy)} استخراجی + {len(hand)} دستی + {len(shared)} مشترک)"
)
