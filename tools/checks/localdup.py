#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""دو `val` هم‌نام در یک بلوک — «Conflicting declarations».

**وضعی که از آن آمدیم.** صفحهٔ جزئیاتِ سفارش از `:app` به `:core` برده
شد و برای رد شدن از مرزِ عکس این خط اضافه شد:

    val photos = LocalPhotos.current

ولی همان تابع از قبل این را داشت:

    val photos by vm.photos.collectAsState()

نتیجه سه خطای کامپایل بود — دو تا «Conflicting declarations» و یکی
«Argument type mismatch»، چون `OrderPhotoStrip(photos = photos)` حالا
به مرز اشاره می‌کرد نه به فهرستِ عکس‌ها.

**چرا هیچ‌کدام از ۴۴ بررسیِ دیگر نگرفتش.** همه‌شان نام‌ها را
**می‌بینند** ولی دامنه را نه: `refcheck` می‌گوید نماد ایمپورت شده،
`uicheck` می‌گوید کتابخانه‌اش هست، `coreref` می‌گوید در ماژولِ درست
است. هیچ‌کدام نمی‌پرسند «آیا این نام در همین بلوک قبلاً گرفته شده؟»

این خرابی مخصوصِ **جابه‌جا کردنِ کد** است: تابعی که سال‌ها سالم بوده،
به محضِ اینکه یک خطِ تازه با نامی رایج (`photos`، `settings`, `context`)
به آن اضافه شود، می‌شکند. و چون فقط کامپایلر می‌گیردش، در محیطی که
Gradle اجرا نمی‌شود تا CI پنهان می‌ماند.

**روش:** بلوک‌ها با شناسهٔ یکتا دنبال می‌شوند، نه با عمق. دو `val` در
دو شاخهٔ `if` هم‌عمق‌اند ولی هم‌بلوک نیستند و مشکلی ندارند — شمردنِ
آن‌ها یعنی هشدارِ نادرست، و هشدارِ نادرست بررسی را خاموش می‌کند.
"""
import re
import sys

import _src

# `val x`، `var x`، و `val x by …` — همه اعلانِ نام‌اند.
DECL = re.compile(r"\b(?:val|var)\s+([a-z]\w*)\s*(?=[:=]|\bby\b)")

# چیزهایی که اعلانِ محلی نیستند و نباید شمرده شوند
SKIP_LINE = re.compile(r"^\s*(?://|\*|/\*)")


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


fail = []
scanned = 0

for path in sorted(_src.CORE.rglob("*.kt")) + sorted(_src.APP.rglob("*.kt")):
    raw = path.read_text(encoding="utf-8")
    src = strip(raw)
    scanned += 1

    # پیمایشِ یک‌بارهٔ فایل با پشتهٔ بلوک‌ها
    block_stack = [0]
    next_id = 1
    # (block_id, name) -> شمارهٔ خط
    seen = {}
    line = 1

    i = 0
    while i < len(src):
        ch = src[i]
        if ch == "\n":
            line += 1
            i += 1
            continue
        # پرانتز هم دامنه است، نه فقط آکولاد.
        #
        # `data class Foo(val name: String)` و `data class Bar(val
        # name: String)` هر دو `name` اعلام می‌کنند ولی در دو دامنهٔ
        # جدا. نسخهٔ اولِ این بررسی فقط `{}` را می‌شمرد و همین ۵۸
        # هشدارِ نادرست داد — روی کدی که سال‌هاست کامپایل می‌شود.
        if ch in "{(":
            block_stack.append(next_id)
            next_id += 1
            i += 1
            continue
        if ch in "})":
            # هرچه در این بلوک اعلام شده با بسته شدنش می‌رود
            closing = block_stack.pop() if len(block_stack) > 1 else 0
            for key in [k for k in seen if k[0] == closing]:
                del seen[key]
            i += 1
            continue

        m = DECL.match(src, i)
        if m:
            name = m.group(1)
            key = (block_stack[-1], name)
            if key in seen:
                fail.append(
                    f"{path.relative_to(_src.REPO)}:{line} — «{name}» در همین "
                    f"بلوک قبلاً در خطِ {seen[key]} اعلام شده. کاتلین این را "
                    f"«Conflicting declarations» می‌خواند و کامپایل نمی‌شود."
                )
            else:
                seen[key] = line
            i = m.end()
            continue
        i += 1

if not scanned:
    print("✗ هیچ فایلی اسکن نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

if fail:
    print(f"✗ {len(fail)} نامِ تکراری در یک بلوک")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ نام‌ها: {scanned} فایل — هیچ دو اعلانی در یک بلوک هم‌نام نیستند")
