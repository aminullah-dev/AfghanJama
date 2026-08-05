#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SQLِ مهاجرت‌های تاریخی در `:core` باید عینِ `Migrations.kt` باشد.

**چرا این فایل وجود دارد.** نسخهٔ ویندوز نمی‌تواند `Migrations.kt` را
اجرا کند: آن ۴۲ مهاجرت روی `SupportSQLiteDatabase` نوشته شده‌اند، APIای
که روی JVM نیست. پس SQLِ آن‌هایی که SQLِ خالص‌اند در `:core` کپی شده تا
ویندوز هم بتواند دفترِ قدیمی را بالا ببرد.

**کپی بدونِ نگهبان یعنی دو تعریف.** همان چیزی که این پروژه از آن
می‌گریزد. پس این بررسی هر بار SQL را از `Migrations.kt` **دوباره
استخراج می‌کند** و با فایلِ تولیدشده مقایسه. اگر کسی روزی مهاجرتی را
دست بزند و کپی به‌روز نشود، همین‌جا قرمز می‌شود.

**سخت‌گیریِ عمدی:** مهاجرتی «خالص» شمرده می‌شود فقط اگر بدنه‌اش چیزی جز
`db.execSQL("...")`ِ بی‌پارامتر نباشد. یک `val`، یک `if`، یک کرسر یا
`arrayOf` کافی است تا کنار گذاشته شود. پنج مهاجرت به همین دلیل بیرون
مانده‌اند و ویندوز نمی‌تواند از رویشان رد شود — و این در
`DESKTOP_BASELINE` صادقانه بازتاب می‌یابد، نه اینکه نصفه‌کاره حدس زده
شود.

بازتولید:  python tools/checks/legacysql.py --write
"""
import pathlib
import re
import sys

import _src

SOURCE = _src.find("data/Migrations.kt")
GENERATED = _src.CORE / "com/afghanjama/data/LegacySchemaSteps.kt"

HEADER = """// ساخته‌شده — دستی ویرایش نکنید.
//
// SQLِ مهاجرت‌های تاریخیِ اندروید (`app/.../Migrations.kt`) برای نسخهٔ
// ویندوز. آنجا روی `SupportSQLiteDatabase` نوشته شده‌اند که روی JVM
// وجود ندارد؛ اینجا فقط خودِ دستورها هستند و هر سکو با API خودش
// اجراشان می‌کند.
//
// فقط مهاجرت‌های **SQLِ خالص** اینجا هستند. آن‌هایی که کرسر یا مقدارِ
// زمانِ اجرا می‌خواهند بیرون مانده‌اند و ویندوز نمی‌تواند از رویشان رد
// شود — `DESKTOP_BASELINE` همین را می‌گوید.
//
// بازتولید:  python tools/checks/legacysql.py --write
// نگهبان:    tools/checks/legacysql.py (در `run_all` اجرا می‌شود)

package com.afghanjama.data

/**
 * گام‌های تاریخی، استخراج‌شده از `Migrations.kt`.
 *
 * اندروید این‌ها را **استفاده نمی‌کند** — آنجا همان `ALL_MIGRATIONS`ِ
 * دست‌نویس اجرا می‌شود که روی گوشیِ کارفرما آزموده شده. این فهرست فقط
 * برای ویندوز است.
 */
val LEGACY_STEPS: List<SchemaStep> = listOf(
"""


def strip_comments(s):
    """توضیح‌ها برداشته می‌شوند ولی رشته‌ها دست‌نخورده می‌مانند."""
    out, i, n = [], 0, len(s)
    in_str = in_line = in_block = False
    while i < n:
        c = s[i]
        nxt = s[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
                out.append(c)
        elif in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 1
        elif in_str:
            out.append(c)
            if c == "\\":
                if i + 1 < n:
                    out.append(nxt)
                    i += 1
            elif c == '"':
                in_str = False
        else:
            if c == "/" and nxt == "/":
                in_line = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block = True
                i += 1
            elif c == '"':
                in_str = True
                out.append(c)
            else:
                out.append(c)
        i += 1
    return "".join(out)


STR = re.compile(r'"((?:[^"\\]|\\.)*)"')


def _balanced(text, start):
    depth, j = 0, start
    while j < len(text):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1 : j], j
        j += 1
    raise ValueError("پرانتزِ بسته‌نشده در Migrations.kt")


def extract():
    """(from, to, [sql]) برای هر مهاجرتِ خالص، و فهرستِ کنارگذاشته‌ها."""
    clean = strip_comments(SOURCE.read_text(encoding="utf-8"))
    pure, skipped = [], []

    for m in re.finditer(r"object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)\s*\{", clean):
        a, b = int(m.group(1)), int(m.group(2))
        i = m.end() - 1
        depth, j = 0, i
        while j < len(clean):
            if clean[j] == "{":
                depth += 1
            elif clean[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = clean[i + 1 : j]

        fm = re.search(r"override\s+fun\s+migrate\s*\([^)]*\)\s*\{", body)
        inner = body[fm.end():].rstrip().rstrip("}") if fm else body

        stmts, ok, why, pos, spans = [], True, "", 0, []
        for call in re.finditer(r"db\.execSQL\s*\(", inner):
            if call.start() < pos:
                continue
            arg, end = _balanced(inner, call.end() - 1)
            if re.search(r",\s*arrayOf", arg):
                ok, why = False, "execSQL با پارامترِ bind"
                break
            if STR.sub("", arg).strip().replace("+", "").strip():
                ok, why = False, "آرگومانِ SQL رشتهٔ ثابت نیست"
                break
            stmts.append("".join(
                p.replace('\\"', '"').replace("\\\\", "\\") for p in STR.findall(arg)
            ))
            spans.append((call.start(), end + 1))
            pos = end + 1

        if ok:
            leftover, last = [], 0
            for s, e in spans:
                leftover.append(inner[last:s])
                last = e
            leftover.append(inner[last:])
            rest = "".join(leftover).strip()

            # **تنها استثنای مجاز: `val now = System.currentTimeMillis()`.**
            #
            # سه مهاجرت فقط به همین یک مقدار نیاز دارند و بقیه‌شان SQLِ
            # خالص است. اگر این استثنا نبود، آن سه دستی نوشته می‌شدند —
            # یعنی ده دستورِ SQL دو نسخه‌ای می‌شد، برای یک عدد.
            #
            # به‌جایش `$now` به نشانهٔ `{now}` تبدیل می‌شود و هر سکو سرِ
            # اجرا یک بار مهرِ زمان می‌گذارد. یک تعریف می‌ماند.
            uses_now = bool(re.search(r"val\s+now\s*=\s*System\.currentTimeMillis\(\)", rest))
            if uses_now:
                rest = re.sub(
                    r"val\s+now\s*=\s*System\.currentTimeMillis\(\)", "", rest
                ).strip()

            if rest:
                ok, why = False, f"کدِ دیگری در بدنه: {rest.split(chr(10))[0][:40]}"
            else:
                subbed = []
                for s in stmts:
                    s2 = s.replace("$now", "{now}")
                    # هر قالبِ رشتهٔ دیگری یعنی چیزی که اینجا نمی‌فهمیم؛
                    # بی‌صدا رد نمی‌شود.
                    if "$" in s2:
                        ok, why = False, f"قالبِ رشتهٔ ناشناخته در SQL: {s2[:40]}"
                        break
                    subbed.append(s2)
                if ok:
                    if uses_now and not any("{now}" in s for s in subbed):
                        ok, why = False, "`now` گرفته شده ولی در SQL به کار نرفته"
                    else:
                        stmts = subbed

        (pure if ok else skipped).append(
            (a, b, stmts) if ok else (a, b, why)
        )

    return pure, skipped


def kotlin_literal(s):
    """رشتهٔ کاتلین. `$` باید فرار داده شود وگرنه قالبِ رشته می‌شود."""
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def render(pure, skipped):
    out = [HEADER]
    for a, b, stmts in pure:
        out.append(f"    SchemaStep(\n        {a}, {b},\n        listOf(\n")
        for s in stmts:
            out.append(f"            {kotlin_literal(s)},\n")
        out.append("        )\n    ),\n")
    out.append(")\n")
    out.append("\n/*\n * کنار گذاشته‌شده‌ها — SQLِ خالص نیستند:\n")
    for a, b, why in skipped:
        out.append(f" *   {a} → {b}: {why}\n")
    out.append(" */\n")
    return "".join(out)


def main():
    pure, skipped = extract()
    want = render(pure, skipped)

    if "--write" in sys.argv:
        GENERATED.write_text(want, encoding="utf-8")
        print(f"✓ نوشته شد: {GENERATED.name} — {len(pure)} گام، "
              f"{sum(len(s) for _, _, s in pure)} دستور")
        return 0

    if not GENERATED.exists():
        print(f"✗ {GENERATED.name} نیست — با --write بسازیدش")
        return 1

    have = GENERATED.read_text(encoding="utf-8").replace("\r\n", "\n")
    if have != want.replace("\r\n", "\n"):
        print("✗ SQLِ تاریخی در :core با Migrations.kt یکی نیست")
        print("  یعنی یکی از دو طرف دست خورده و کپی کهنه شده.")
        print("  بازتولید:  python tools/checks/legacysql.py --write")
        return 1

    print(f"✓ SQLِ تاریخی عینِ Migrations.kt است — {len(pure)} گامِ خالص، "
          f"{sum(len(s) for _, _, s in pure)} دستور، {len(skipped)} کنارگذاشته")
    return 0


sys.exit(main())
