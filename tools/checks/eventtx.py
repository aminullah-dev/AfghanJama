#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر رویداد باید داخلِ همان تراکنشی نوشته شود که دادهٔ اصلی.

**کلِ ارزشِ صندوقِ خروجی همین یک بند است.** اگر رویداد بیرون از
تراکنش نوشته شود، دقیقاً همان اشکالی برمی‌گردد که مرزِ `Tx` برای
بستنش ساخته شد:

    db.atomic { … سند ثبت شد … }      ← تراکنش بسته شد
    emit("فروش ثبت شد", …)            ← اگر اینجا اپ بمیرد؟

سند در دفتر هست، رویدادش نیست. مصرف‌کننده — امروز حسابرسی، فردا
همگام‌سازیِ چنددستگاهی — هرگز از آن فروش خبردار نمی‌شود. و برعکسش هم
هست: رویداد نوشته شود و تراکنش برگردد، یعنی خبری از کاری که نشده.

این همان «مسئلهٔ نوشتنِ دوگانه» (dual write) است و تنها راهِ درستش
این است که هر دو نوشتن در **یک** تراکنش باشند.

**چرا آزمون این را نمی‌گیرد:** کد کامپایل می‌شود، تست‌ها سبزند، و در
۹۹٫۹٪ اجراها هم درست کار می‌کند. فقط آن یک باری که گوشی وسطِ کار
خاموش می‌شود دفتر و رویداد از هم می‌افتند — و آن‌وقت هیچ‌کس نمی‌فهمد
چرا.

قاعده: هر فراخوانِ `emit(` باید در بدنه‌ای باشد که از `atomic {`
گذشته — یا مستقیم، یا در تابعی که فقط از داخلِ `atomic` صدا زده
می‌شود (همان الگوی «پوسته/بدنه» با پسوندِ `Tx`).
"""
import re
import sys

import _src

REPO = _src.CORE / "com/afghanjama/data/repo/Repo.kt"

if not REPO.exists():
    print("✗ Repo.kt نیست — بررسی پوچ می‌شد.")
    sys.exit(1)

raw = REPO.read_text(encoding="utf-8")

# توضیح‌ها و رشته‌ها برداشته شوند تا نمونهٔ داخلِ مستندات شمرده نشود
src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), raw)
src = re.sub(r"//[^\n]*", "", src)
src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)

fail = []

# ── ۱ خودِ `emit` باید وجود داشته باشد ────────────────────────────
if "private suspend fun emit(" not in src:
    print("✗ `emit` در Repo نیست — یا برداشته شده یا جابه‌جا؛ بررسی پوچ می‌شود.")
    sys.exit(1)


def enclosing_fun(text: str, pos: int):
    """نامِ تابعی که این نقطه داخلش است."""
    best = None
    for m in re.finditer(r"\bfun\s+(\w+)\s*\(", text[:pos]):
        best = m.group(1)
    return best


def body_of(text: str, name: str):
    """(شروع، پایانِ) بدنهٔ یک تابع با نامِ داده‌شده."""
    m = re.search(r"\bfun\s+" + re.escape(name) + r"\s*\(", text)
    if not m:
        return None
    i = text.find("{", m.end())
    if i < 0:
        return None
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return i, j
    return None


# ── ۲ کدام توابع از داخلِ `atomic` صدا زده می‌شوند ────────────────
#
# هر `atomic { … }` بدنه‌ای دارد؛ نامِ توابعی که آنجا صدا زده می‌شوند
# «امن» شمرده می‌شوند، چون خودشان داخلِ تراکنش اجرا می‌شوند.
inside_tx = set()
for m in re.finditer(r"\batomic\s*\{", src):
    depth = 0
    start = m.end() - 1
    for j in range(start, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                for c in re.finditer(r"\b(\w+)\s*\(", src[start:j]):
                    inside_tx.add(c.group(1))
                break

# ── ۳ هر `emit` کجاست ────────────────────────────────────────────
found = 0
for m in re.finditer(r"(?<!fun )\bemit\s*\(", src):
    # خودِ تعریف را نشمار
    if re.search(r"fun\s+emit\s*\($", src[:m.end()]):
        continue
    found += 1
    host = enclosing_fun(src, m.start())
    if host is None:
        fail.append("یک `emit` بیرون از هر تابعی — این نباید ممکن باشد.")
        continue

    b = body_of(src, host)
    direct = b is not None and "atomic {" in src[b[0]:b[1]]
    if direct or host in inside_tx:
        continue

    fail.append(
        f"`emit` در «{host}» — نه خودش `atomic` دارد و نه از داخلِ "
        f"تراکنشی صدا زده می‌شود. رویداد و دادهٔ اصلی از هم می‌افتند."
    )

if found == 0:
    fail.append("هیچ `emit`ی صدا زده نمی‌شود — صندوقِ خروجی مرده است.")

if fail:
    print(f"✗ {len(fail)} مشکل در ثبتِ رویداد")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ رویداد: هر {found} فراخوانِ emit داخلِ تراکنش است")
