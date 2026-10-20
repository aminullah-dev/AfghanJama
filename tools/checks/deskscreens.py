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
#: از ۱.۱۰ فهرستِ بخش‌ها در `:core` است (`DeskSection`)، تا آزمونِ واحد
#: ببیندش؛ `Shell.kt` فقط نامِ کوتاهِ `Section` را برایش دارد.
SECTIONS = _src.CORE / "com/afghanjama/ui/nav/DeskSections.kt"

#: صفحه‌هایی که عمداً در نوارِ کناری نیستند — با دلیل.
EXCLUDED = {
    "PostLoginQuoteScreen":
        "صفحهٔ خوش‌آمدِ گوشی است؛ روی پی‌سی که کاربر تمامِ روز پایش "
        "نشسته، یک پردهٔ اضافه بینِ او و کار است.",
}
# «HomeDashboardScreen» تا ۱.۹ اینجا بود («دو ناوبری کنارِ هم سردرگمی
# است»). از ۱.۱۰ صفحهٔ اولِ کامپیوتر است: صفحهٔ اولِ قبلی خودآزمایی بود و
# کارفرما روی آن هیچ عددی از کارگاه نمی‌دید.

for p in (SHELL, MAIN, SCREENS, SECTIONS):
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
sections = strip(SECTIONS.read_text(encoding="utf-8"))
if not re.search(r"typealias Section\s*=\s*DeskSection", shell):
    print("✗ `Section` در Shell دیگر همان `DeskSection` نیست — ساختار عوض شده.")
    sys.exit(1)
m = re.search(r"enum class DeskSection\s*\([^)]*\)\s*\{", sections)
if not m:
    print("✗ enum DeskSection پیدا نشد — ساختارِ DeskSections عوض شده.")
    sys.exit(1)
depth, end = 0, None
for i in range(m.end() - 1, len(sections)):
    if sections[i] == "{":
        depth += 1
    elif sections[i] == "}":
        depth -= 1
        if depth == 0:
            end = i
            break
body = sections[m.end():end] if end else ""
members = set(re.findall(r"^\s{4}(\w+)\s*\(", body, re.M))
branches = set(re.findall(r"Section\.(\w+)\s*->", shell))

if not members:
    fail.append("هیچ عضوی در Section خوانده نشد — بررسی پوچ می‌شد.")
for miss in sorted(members - branches):
    fail.append(f"بخشِ «{miss}» شاخه‌ای در SectionContent ندارد — صفحهٔ خالی.")

if re.search(r"else\s*->", body):
    pass  # داخلِ خودِ enum معنایی ندارد

# ── ۴ «اشتراکِ کارگاه» بالای نوار بماند ──────────────────────────
#
# تا ۱.۹.۱ سی‌ونهمین بند از چهل‌وسه بود و کارفرما روی مک پیدایش نکرد؛ گوشیِ
# هر کارگر از همان‌جا وصل می‌شود. نوار به ترتیبِ همین enum ساخته می‌شود، پس
# جای بند همین ترتیب است. بندی که روزی «مرتب‌سازی» پایینش بفرستد اینجا
# قرمز می‌شود.
TOP = 3
order = re.findall(r"^\s{4}(\w+)\s*\(", body, re.M)
if "WorkshopLink" not in order:
    fail.append("بخشِ WorkshopLink در Section پیدا نشد — صفحهٔ اشتراکِ کارگاه از نوار افتاده.")
elif order.index("WorkshopLink") >= TOP:
    fail.append(
        f"«اشتراکِ کارگاه» بندِ {order.index('WorkshopLink') + 1}ِ نوار است؛ باید میانِ "
        f"{TOP} بندِ اول بماند (همان جایی که کارفرما پیدایش می‌کند)."
    )

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
