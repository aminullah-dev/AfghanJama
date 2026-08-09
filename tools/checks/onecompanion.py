#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر کلاس فقط یک `companion object` دارد.

**وضعی که از آن آمدیم.** برای مانده افتتاحیه یک ثابتِ تازه لازم بود و
`companion object` تازه‌ای بالای `Repo` اضافه شد — در حالی که همان
کلاس از قبل یکی داشت، ۷۰۰ خط پایین‌تر. کاتلین بیش از یکی را قبول
نمی‌کند و کامپایل می‌شکند.

**چرا `dupdecl` نگرفتش:** آن نام‌های تکراری را می‌بیند، و اینجا هیچ
نامی تکرار نشده بود — `OPENING` و `RETENTION_MONTHS` دو نامِ متفاوت در
دو جسمِ متفاوت‌اند. چیزی که تکرار شده بود خودِ **جسم** بود.

این هم مثلِ `localdup` از دستهٔ خطاهایی است که فقط کامپایلر می‌گیرد، و
در محیطی که Gradle اجرا نمی‌شود تا CI پنهان می‌ماند. در فایلِ بلند
هم به چشم نمی‌آید: کسی که بالای فایل ثابت اضافه می‌کند، انتهای فایل
را نمی‌بیند.
"""
import re
import sys

import _src


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


#: شروعِ یک نوعِ تازه — کلاس، شیء، رابط
TYPE = re.compile(r"\b(?:class|object|interface)\s+(\w+)")
COMPANION = re.compile(r"\bcompanion\s+object\b")

fail = []
scanned = 0

for path in sorted(_src.CORE.rglob("*.kt")) + sorted(_src.APP.rglob("*.kt")):
    src = strip(path.read_text(encoding="utf-8"))
    scanned += 1

    # پشتهٔ نوع‌ها: هر `{` که بعد از اعلانِ یک نوع بیاید بدنهٔ آن است.
    stack = []          # (نامِ نوع، عمقِ آکولاد، شمارشِ companion، خطِ اولی)
    depth = 0
    line = 1
    pending = None      # نامِ نوعی که هنوز `{`اش نیامده

    i = 0
    while i < len(src):
        ch = src[i]
        if ch == "\n":
            line += 1
            i += 1
            continue

        m = TYPE.match(src, i)
        if m:
            pending = m.group(1)
            i = m.end()
            continue

        m = COMPANION.match(src, i)
        if m:
            if stack:
                owner, d, count, first = stack[-1]
                if count == 0:
                    stack[-1] = (owner, d, 1, line)
                else:
                    fail.append(
                        f"{path.relative_to(_src.REPO)}:{line} — «{owner}» "
                        f"دو `companion object` دارد (اولی در خطِ {first}). "
                        f"کاتلین فقط یکی می‌پذیرد."
                    )
                    stack[-1] = (owner, d, count + 1, first)
            i = m.end()
            # `companion object` خودش `{` دارد ولی نوعِ تازه نیست
            pending = None
            continue

        if ch == "{":
            depth += 1
            if pending is not None:
                stack.append((pending, depth, 0, 0))
                pending = None
            i += 1
            continue

        if ch == "}":
            if stack and stack[-1][1] == depth:
                stack.pop()
            depth -= 1
            i += 1
            continue

        i += 1

if not scanned:
    print("✗ هیچ فایلی اسکن نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

if fail:
    print(f"✗ {len(fail)} کلاس با بیش از یک companion")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ companion: {scanned} فایل — هیچ کلاسی بیش از یکی ندارد")
