#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر صفحه‌ای که در `:core` است، روی ویندوز هم راهی داشته باشد.

**وضعی که از آن آمدیم.** `:core` سی‌وچهار صفحه داشت و نوارِ کناریِ
ویندوز سی‌تا را نشان می‌داد. چهار صفحه — از جمله **مرکزِ هشدار** و
**معاملاتِ روزمره** — کاملاً پرتابل بودند، در همان ماژولِ مشترک
می‌نشستند، و فقط کسی یادش رفته بود در `Section` بنویسدشان. نه کامپایل
چیزی گفت، نه آزمونی؛ فقط کارگاه روی پی‌سی آن دکمه‌ها را پیدا نمی‌کرد.

این دقیقاً همان دسته خرابی است که با دو ماژول شدنِ پروژه محتمل‌تر شد:
کار در `:core` تمام می‌شود و کسی فرض می‌کند «پس روی هر دو سکو هست».
در حالی که اندروید `AppNav` دارد و ویندوز `Section` — و آن دو خودکار
هم را نمی‌بینند.

**قاعده:** هر `…Screen` در `:core` یا باید از `Shell` صدا زده شود، یا
در [EXCLUDED] با دلیل نوشته شود. فهرستِ استثنا عمداً دستی است: جا
ماندنِ یک صفحه باید یک **تصمیم** باشد که کسی نوشته، نه سکوت.
"""
import re
import sys

import _src

SHELL = _src.DESKTOP / "com/afghanjama/desktop/Shell.kt"
MAIN = _src.DESKTOP / "com/afghanjama/desktop/Main.kt"
SCREENS = _src.CORE / "com/afghanjama/ui/screens"

#: صفحه‌هایی که عمداً در نوارِ کناری نیستند — با دلیل.
EXCLUDED = {
    "PostLoginQuoteScreen":
        "صفحهٔ خوش‌آمدِ گوشی است؛ روی پی‌سی که کاربر تمامِ روز پایش "
        "نشسته، یک پردهٔ اضافه بینِ او و کار است.",
    "HomeDashboardScreen":
        "شبکهٔ میان‌بُرهای گوشی. روی ویندوز خودِ نوارِ کناری همان کار "
        "را می‌کند و همیشه دیده می‌شود؛ دو ناوبری کنارِ هم سردرگمی است.",
}

for p in (SHELL, MAIN, SCREENS):
    if not p.exists():
        print(f"✗ {p.name} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


shell = strip(SHELL.read_text(encoding="utf-8"))
main = strip(MAIN.read_text(encoding="utf-8"))
reachable = set(re.findall(r"\b(\w+Screen)\s*\(", shell + main))

available = {p.stem for p in SCREENS.glob("*.kt")}
if not available:
    print("✗ هیچ صفحه‌ای در :core پیدا نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

fail = []

# ── ۱ هر صفحه یا در دسترس است یا استثنای نوشته‌شده ────────────────
for name in sorted(available):
    if name in reachable or name in EXCLUDED:
        continue
    fail.append(
        f"«{name}» در `:core` است ولی ویندوز راهی به آن ندارد. "
        f"یا در `Section` بیاوریدش، یا در EXCLUDED با دلیل بنویسید — "
        f"جا ماندنش باید تصمیم باشد نه فراموشی."
    )

# ── ۲ استثنای کهنه هم بد است ─────────────────────────────────────
for name in sorted(EXCLUDED):
    if name not in available:
        fail.append(
            f"EXCLUDED نامِ «{name}» را دارد ولی چنین صفحه‌ای در "
            f"`:core` نیست — فهرست کهنه شده."
        )
    elif name in reachable:
        fail.append(
            f"«{name}» هم در EXCLUDED است هم واقعاً نشان داده می‌شود — "
            f"یکی‌شان باید برداشته شود."
        )

# ── ۳ هر عضوِ Section باید شاخه داشته باشد ────────────────────────
#
# `when` روی enum بدونِ `else` اگر شاخه‌ای کم داشته باشد کامپایل
# نمی‌شود، ولی اگر کسی `else -> Unit` بگذارد، بخشِ تازه بی‌صدا صفحهٔ
# خالی نشان می‌دهد. این بند همان را می‌گیرد.
m = re.search(r"internal enum class Section\s*\([^)]*\)\s*\{", shell)
if not m:
    print("✗ enum Section پیدا نشد — ساختارِ Shell عوض شده.")
    sys.exit(1)
depth, end = 0, None
for i in range(m.end() - 1, len(shell)):
    if shell[i] == "{":
        depth += 1
    elif shell[i] == "}":
        depth -= 1
        if depth == 0:
            end = i
            break
body = shell[m.end():end] if end else ""
members = set(re.findall(r"^\s{4}(\w+)\s*\(", body, re.M))
branches = set(re.findall(r"Section\.(\w+)\s*->", shell))

if not members:
    fail.append("هیچ عضوی در Section خوانده نشد — بررسی پوچ می‌شد.")
for miss in sorted(members - branches):
    fail.append(f"بخشِ «{miss}» شاخه‌ای در SectionContent ندارد — صفحهٔ خالی.")

if re.search(r"else\s*->", body):
    pass  # داخلِ خودِ enum معنایی ندارد

if fail:
    print(f"✗ {len(fail)} شکاف در صفحه‌های ویندوز")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ ویندوز: {len(available)} صفحهٔ :core — "
    f"{len(available) - len(EXCLUDED)} در دسترس، "
    f"{len(EXCLUDED)} استثنای نوشته‌شده، {len(members)} بخش همه با شاخه"
)
