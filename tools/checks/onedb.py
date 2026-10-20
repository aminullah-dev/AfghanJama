#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""یک دفتر در هر پروسهٔ اندروید — و کسی آن را نمی‌بندد.

**اشکالی که این بررسی را ساخت** (تا ۱.۹.۱، روی گوشیِ اصلیِ اندرویدی):

`MainActivity` دفتر را با `buildAppDatabase` باز می‌کرد، سرورِ اشتراکِ
کارگاه (`AndroidLanHost`) یک نمونهٔ دیگر، و هر کارِ پس‌زمینه یکی دیگر.
Room فقط تغییرهای **همان نمونه** را به `Flow`هایش خبر می‌دهد؛ پس درخواستی
که گوشیِ کارگر از راهِ سرور می‌نشاند در فهرستِ «درخواست‌های منتظر» دیده
نمی‌شد تا کارفرما صفحه را ترک کند و برگردد. کامپایل بی‌عیب بود و هیچ
آزمونی روی JVM آن را نمی‌دید.

دو قاعده:

  ۱ `buildAppDatabase(` جز در `data/AppDb.kt` صدا زده نشود؛ هر کس دفتر
    می‌خواهد `AppDb.get(context)` بگیرد.
  ۲ نمونه‌ای که از `AppDb.get` آمده بسته نشود (`.close()`)؛ بستن فقط
    کارِ بازیابیِ پشتیبان است، از راهِ `repo.closeDatabase()` و پس از آن
    `AppDb.forget()`.
"""
import re
import sys

import _src

APP_DB = _src.APP / "com/afghanjama/data/AppDb.kt"
BUILDER = _src.APP / "com/afghanjama/data/AppDatabase.kt"


def strip(src: str) -> str:
    """توضیح‌ها و رشته‌ها بیرون — نامی که فقط در توضیح آمده کد نیست."""
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


def builder_calls(name: str, kt: str) -> list[str]:
    """صدا زدنِ `buildAppDatabase` بیرون از `AppDb`؛ خالی یعنی سالم."""
    if name in ("AppDb.kt",):
        return []
    src = strip(kt)
    out = []
    for m in re.finditer(r"(?<![\w.])buildAppDatabase\s*\(", src):
        line_start = src.rfind("\n", 0, m.start()) + 1
        line = src[line_start:src.find("\n", m.start())]
        if re.match(r"\s*(internal\s+|private\s+)?fun\s+buildAppDatabase", line):
            continue  # خودِ تعریف
        lineno = src.count("\n", 0, m.start()) + 1
        out.append(f"خطِ {lineno}: `buildAppDatabase` جدا صدا زده شده — `AppDb.get(context)` بگیرید.")
    return out


def closes(kt: str) -> list[str]:
    """بستنِ نمونهٔ مشترک؛ خالی یعنی سالم."""
    src = strip(kt)
    out = []
    if re.search(r"AppDb\.get\([^)]*\)\s*\.\s*close\s*\(", src):
        out.append("`AppDb.get(...).close()` — نمونهٔ مشترکِ همهٔ پروسه بسته می‌شود.")
    for m in re.finditer(r"\b(?:val|var)\s+(\w+)\s*(?::\s*[\w.]+)?\s*=\s*AppDb\.get\(", src):
        name = m.group(1)
        if re.search(rf"(?<![\w.]){re.escape(name)}\s*\.\s*close\s*\(", src[m.end():]):
            out.append(f"`{name}` از `AppDb.get` آمده و `{name}.close()` می‌شود — "
                       f"صفحه‌ها و سرورِ همین پروسه با دفترِ بسته می‌مانند.")
    return out


# ── خودآزمایی: بررسی‌ای که خرابی را نگیرد بدتر از نبودنش است ──────
GOOD = (
    "class W {\n    suspend fun doWork() {\n"
    "        // قبلاً: val db = buildAppDatabase(ctx)\n"
    "        val db = AppDb.get(applicationContext)\n"
    "        db.dao().list()\n    }\n}\n"
)
BROKEN_BUILD = {
    "سازندهٔ جدا": "class W {\n    fun x() { val db = buildAppDatabase(ctx) }\n}\n",
    "سازندهٔ تنبل": "class H {\n    private val db by lazy { buildAppDatabase(ctx.applicationContext) }\n}\n",
}
BROKEN_CLOSE = {
    "بستنِ متغیر": "class W {\n    fun x() {\n        val db = AppDb.get(ctx)\n"
                   "        try { db.dao() } finally { db.close() }\n    }\n}\n",
    "بستنِ مستقیم": "class W {\n    fun x() { AppDb.get(ctx).close() }\n}\n",
}

fail = []
if builder_calls("W.kt", GOOD) or closes(GOOD):
    fail.append("خودآزمایی: کدِ درست رد شد — بررسی خراب است.")
for name, x in BROKEN_BUILD.items():
    if not builder_calls("W.kt", x):
        fail.append(f"خودآزمایی: نمونهٔ خرابِ «{name}» گرفته نشد — بررسی پوچ است.")
for name, x in BROKEN_CLOSE.items():
    if not closes(x):
        fail.append(f"خودآزمایی: نمونهٔ خرابِ «{name}» گرفته نشد — بررسی پوچ است.")

# ── خودِ پروژه ────────────────────────────────────────────────────
for path in (APP_DB, BUILDER):
    if not path.exists():
        fail.append(f"{path.relative_to(_src.REPO)} نیست — بررسی پوچ می‌شد.")

if not fail:
    if "fun get(" not in strip(APP_DB.read_text(encoding="utf-8")):
        fail.append("`AppDb.get` پیدا نشد — دفترِ مشترک کجاست؟")
    seen_get = 0
    for path in sorted(_src.APP.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(_src.REPO)
        fail += [f"{rel}: {p}" for p in builder_calls(path.name, text)]
        fail += [f"{rel}: {p}" for p in closes(text)]
        seen_get += len(re.findall(r"AppDb\.get\(", strip(text)))
    # اگر هیچ‌کس `AppDb.get` را صدا نزند، یعنی دفتر از راهِ دیگری باز
    # می‌شود و این بررسی چیزی را نمی‌پاید.
    if seen_get < 3:
        fail.append(f"فقط {seen_get} جا `AppDb.get` دارد — دفتر از راهِ دیگری باز می‌شود؟")

if fail:
    print("✗ onedb:")
    for f in fail:
        print("  " + f)
    sys.exit(1)
print("✓ یک دفتر در پروسهٔ اندروید، و کسی آن را نمی‌بندد")
