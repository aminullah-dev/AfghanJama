#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""بدنهٔ `=`ِ یک تابع نباید انتساب باشد.

**وضعی که از آن آمدیم.** `SampleWorkshopViewModel` این را داشت:

    fun clearMessage() = _ui.value = _ui.value.copy(message = null)

به چشم درست می‌آید و در بیشتر زبان‌ها هم درست است، ولی در کاتلین
**انتساب عبارت نیست**؛ دستور است. پس بدنهٔ `=` نمی‌تواند انتساب باشد:

    Only expressions are allowed in this context.

درستش یک بدنهٔ آکولادی است:

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

**چرا این بررسی لازم شد.** Gradle در محیطِ کارِ این پروژه اجرا نمی‌شود
(مخزنِ maven بسته است)، پس تنها جایی که کامپایلر حرف می‌زند CI است. این
یک خط، یازده کامیت دوام آورد — نه چون کسی ندیدش، بلکه چون `schemacheck`
اولین پله بود و شکستش کلِ کار را قطع می‌کرد، پس **هیچ‌وقت کامپایلی رخ
نداد**. آن قفل باز شد و اولین چیزی که از زیرش بیرون آمد همین بود.

هیچ‌کدام از ۴۷ بررسیِ دیگر این را نمی‌گیرند: پرانتزها متوازن‌اند، هر
نماد ایمپورت شده، و هیچ نامی تکراری نیست. فقط کامپایلر می‌گیردش — و
حالا این.

**چه چیزی را نمی‌گیرد.** فقط انتسابی که در **بالاترین سطحِ** بدنه و
درست بعد از `=` بیاید. انتساب درونِ لامبدا (`obj.also { it.x = 1 }`)
کاملاً قانونی است و باید هم بی‌صدا رد شود؛ همین‌طور آرگومانِ نام‌دار
(`Foo(a = b)`) و هر مقایسه‌ای (`==`، `>=`، `!=`).
"""
import re
import sys

import _src


def strip(src: str) -> str:
    """توضیح و رشته را بردار — قاعدهٔ ۳ در README.

    بی این کار، `// fun f() = a = b` در یک توضیح، یا رشته‌ای که `=`
    دارد، هشدارِ نادرست می‌سازد. شمارشِ `\\n` نگه داشته می‌شود تا شمارهٔ
    خط جابه‌جا نشود.
    """
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


FUN = re.compile(r"\bfun\b")

#: یک «چپ‌وندِ» ساده و بعدش عملگرِ انتساب.
#:
#: زنجیرهٔ نقطه‌ای و اندیس را می‌گیرد (`_ui.value`، `a.b[0]`) ولی از
#: پرانتز رد نمی‌شود — پس `Foo(a = b)` و `x.also { }` اصلاً نمی‌خورند.
#:
#: `=(?!=)` مقایسه را کنار می‌گذارد، و چون پیش از عملگر حتماً یک
#: چپ‌وند آمده، نویسهٔ پیشین هرگز `<` یا `>` یا `!` نیست — یعنی
#: `<=` و `>=` و `!=` هم خودبه‌خود بیرون می‌مانند.
ASSIGN = re.compile(
    r"\s*[A-Za-z_]\w*(?:\s*\??\.\s*[A-Za-z_]\w*|\s*\[[^\]\n]*\])*"
    r"\s*(=(?!=)|[+\-*/%]=)"
)


def close_paren(src: str, i: int) -> int:
    """اندیسِ `)`ِ جفتِ پرانتزی که در `i` باز شده — یا -1."""
    depth = 0
    while i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


fail = []
scanned = 0
bodies = 0

for path in _src.kt_files():
    src = strip(path.read_text(encoding="utf-8"))
    scanned += 1

    for m in FUN.finditer(src):
        # پرانتزِ پارامترها — با شمارش، تا نوعِ تابعی و مقدارِ پیش‌فرضِ
        # پرانتزدار (`x: Int = f(1)`) درست رد شوند.
        open_i = src.find("(", m.end())
        if open_i == -1:
            continue
        close_i = close_paren(src, open_i)
        if close_i == -1:
            continue

        # فقط تا پایانِ همان خط دنبالِ `=` یا `{` می‌گردیم. اگر تابع
        # بدنه ندارد (رابط، abstract) هیچ‌کدام پیدا نمی‌شود و بی‌صدا رد
        # می‌شود — که درست است. بی این مرز، `=`ِ **اعلانِ بعدی** به این
        # تابع نسبت داده می‌شد.
        eol = src.find("\n", close_i)
        if eol == -1:
            eol = len(src)
        head = src[close_i + 1:eol]

        eq = head.find("=")
        brace = head.find("{")
        if eq == -1:
            continue
        if brace != -1 and brace < eq:
            continue        # بدنهٔ آکولادی — انتساب در آن قانونی است

        # بدنه از بعدِ `=` شروع می‌شود و می‌تواند به خطِ بعد برود.
        body_at = close_i + 1 + eq + 1
        bodies += 1

        hit = ASSIGN.match(src, body_at)
        if hit:
            line = src.count("\n", 0, m.start()) + 1
            name = re.match(r"\s*(?:<[^>]*>\s*)?([\w.]*)", src[m.end():])
            fail.append(
                f"{path.relative_to(_src.REPO)}:{line} — بدنهٔ `=`ِ "
                f"«{(name.group(1) if name else '?') or '?'}» یک انتساب است "
                f"(`{hit.group(1)}`). کاتلین انتساب را عبارت نمی‌داند: "
                f"«Only expressions are allowed in this context». "
                f"بدنه را آکولادی کنید."
            )

if not scanned:
    print("✗ هیچ فایلی اسکن نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

if bodies == 0:
    print("✗ هیچ تابعِ بدنه-`=` پیدا نشد — الگو شکسته، بررسی پوچ می‌شود.")
    sys.exit(1)

if fail:
    print(f"✗ {len(fail)} بدنهٔ `=` که انتساب است")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ بدنهٔ `=`: {bodies} تابع در {scanned} فایل — هیچ‌کدام انتساب نیست")
