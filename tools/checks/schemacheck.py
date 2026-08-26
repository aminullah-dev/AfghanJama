#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""طرحِ دیتابیس صادر شود، در گیت بماند، و با `DB_VERSION` بخواند.

**چرا:** تا امروز `exportSchema = false` بود، یعنی طرحِ ۴۳ جدول هیچ‌جا
نسخه‌برداری نمی‌شد. با ۴۲ مهاجرتِ دستی روی **دو** دیتابیس، تنها چیزی که
تضمین می‌کرد مهاجرت‌ها به همان طرحی برسند که موجودیت‌ها توصیف می‌کنند،
آدم بود.

**هزینه‌ای که همین حالا داریم می‌پردازیم:** نسخه‌های ۱۹ تا ۶۰ هرگز صادر
نشدند و دیگر قابلِ بازسازی نیستند. پس آن ۴۲ مهاجرتِ گذشته را نمی‌شود
اجرایی آزمود — فقط ایستا (`migrationgap`, `legacysql`). این بررسی برای
این است که همین هزینه دوباره تکرار نشود.

چهار قاعده:

  ۱ `exportSchema = true` روی هر دو `@Database` بماند
  ۲ `room.schemaLocation` در هر دو فایلِ ساخت تنظیم باشد
  ۳ پوشهٔ `schemas/` در `.gitignore` نیفتد — فایلی که کامیت نشود انگار
    ساخته نشده
  ۴ هر فایلِ طرحی که کامیت شده باید سالم باشد و نسخهٔ داخلش با نامش
    بخواند؛ و از نسخهٔ **بعد از فعال شدنِ صادرات** به بعد، طرحِ نسخهٔ
    جاری و نسخهٔ قبلی هر دو باید موجود باشند — وگرنه دوباره دستمان
    برای آزمونِ مهاجرت خالی است.

قاعده‌های ۱ تا ۳ همین امروز معنا دارند. قاعدهٔ ۴ وقتی `DB_VERSION` از
[EXPORT_ENABLED_AT] جلوتر برود فعال می‌شود — یعنی دقیقاً همان لحظه‌ای
که فایلِ نسخهٔ قبلی بارِ معنایی پیدا می‌کند.
"""
import json
import pathlib
import re
import sys

import _src

#: نسخه‌ای که صادراتِ طرح از آن روشن شد. پیش از این هیچ فایلی وجود ندارد
#: و هرگز هم نخواهد داشت.
EXPORT_ENABLED_AT = 61

MODULES = {
    "app": (
        _src.APP / "com/afghanjama/data/AppDatabase.kt",
        _src.REPO / "app/build.gradle.kts",
        _src.REPO / "app/schemas",
    ),
    "desktop": (
        _src.DESKTOP / "com/afghanjama/desktop/data/DesktopDatabase.kt",
        _src.REPO / "desktop/build.gradle.kts",
        _src.REPO / "desktop/schemas",
    ),
}

fail = []

m = re.search(
    r"const val DB_VERSION\s*=\s*(\d+)",
    (_src.CORE / "com/afghanjama/data/DbSchema.kt").read_text(encoding="utf-8"),
)
if not m:
    print("✗ DB_VERSION پیدا نشد — بررسی پوچ می‌شد.")
    sys.exit(1)
db_version = int(m.group(1))

# ── ۱ و ۲ ─────────────────────────────────────────────────────────
for name, (dbfile, gradle, _) in MODULES.items():
    src = dbfile.read_text(encoding="utf-8")
    if not re.search(r"^\s*exportSchema\s*=\s*true", src, re.M):
        fail.append(
            f"{name}: `exportSchema = true` نیست. بی آن طرحِ هیچ نسخه‌ای "
            f"نوشته نمی‌شود و آزمونِ مهاجرت برای همیشه ناممکن می‌مانَد."
        )
    g = gradle.read_text(encoding="utf-8")
    if "room.schemaLocation" not in g:
        fail.append(
            f"{name}: `room.schemaLocation` در فایلِ ساخت نیست — Room با "
            f"«Schema export directory is not provided» ساخت را می‌شکند."
        )

# ── ۳ ─────────────────────────────────────────────────────────────
ignore = (_src.REPO / ".gitignore")
if ignore.exists():
    for line in ignore.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if s and not s.startswith("#") and "schemas" in s:
            fail.append(
                f".gitignore خطِ «{s}» را دارد — فایلِ طرحی که کامیت نشود "
                f"انگار ساخته نشده."
            )

# ── ۴ ─────────────────────────────────────────────────────────────
found = {}
for name, (_, _, folder) in MODULES.items():
    versions = set()
    for p in sorted(folder.rglob("*.json")) if folder.is_dir() else []:
        stem = p.stem
        if not stem.isdigit():
            fail.append(f"{name}: نامِ «{p.name}» عدد نیست — طرحِ Room نامش نسخه است.")
            continue
        n = int(stem)
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except Exception as e:
            fail.append(f"{name}/{p.name}: خوانده نشد ({e.__class__.__name__}).")
            continue
        inner = data.get("database", {}).get("version")
        if inner != n:
            fail.append(
                f"{name}/{p.name}: نسخهٔ داخلِ فایل {inner} است ولی نامش {n} — "
                f"فایل جابه‌جا یا دستی ویرایش شده."
            )
        versions.add(n)
    found[name] = versions

if db_version > EXPORT_ENABLED_AT:
    for name, versions in found.items():
        for need in (db_version, db_version - 1):
            if need not in versions:
                fail.append(
                    f"{name}: طرحِ نسخهٔ {need} کامیت نشده. یک بار بسازید و "
                    f"`{name}/schemas/` را کامیت کنید — بی فایلِ نسخهٔ قبلی، "
                    f"مهاجرتِ {db_version - 1}→{db_version} آزمودنی نیست."
                )

if fail:
    print(f"✗ {len(fail)} مشکل در طرحِ صادرشده")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

total = sum(len(v) for v in found.values())
if total == 0:
    print(
        f"✓ صادراتِ طرح روشن است (DB_VERSION={db_version}). هنوز فایلی کامیت "
        f"نشده — با اولین ساخت در `app/schemas/` و `desktop/schemas/` "
        f"ساخته می‌شود و باید کامیت شود."
    )
else:
    print(
        f"✓ طرح: {total} فایل سالم "
        f"(app={sorted(found['app'])}, desktop={sorted(found['desktop'])}), "
        f"DB_VERSION={db_version}"
    )
