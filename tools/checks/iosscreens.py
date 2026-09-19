#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر صفحه‌ای که در `:core` است، روی آیفون هم راهی داشته باشد.

قرینهٔ `deskscreens` برای سکوی سوم — و از همان دردی آمد که آن یکی
آمده بود، فقط دیرتر.

**وضعی که از آن آمدیم.** `IosShell` هشت مسیر از چهل‌ودو مسیر را
می‌شناخت و سه تای همان هشت‌تا فقط پیامِ «هنوز نیست» بود. بدتر از خودِ
عدد، یکی از آن پیام‌ها **دروغ شده بود**: توضیحش می‌گفت هر سه صفحهٔ
برش و دوخت و نظارت در `:app`اند، در حالی که `SewingScreen` و
`ReviewScreen` مدت‌ها بود به `commonMain` آمده بودند. دو صفحهٔ سالم
پشتِ یک جملهٔ کهنه بسته مانده بود و هیچ‌چیز خبر نمی‌داد.

**قاعده، سه بند:**

۱. هر `…Screen`ِ `commonMain` یا از `iosMain` صدا زده می‌شود، یا در
   [EXCLUDED] با دلیل نوشته می‌شود.

۲. هر مسیرِ `Routes` یا شاخه‌ای در `IosShell` دارد، یا در
   [ROUTES_EXCLUDED] با دلیل. شاخهٔ `else`ِ عمومی جوابِ صادقی به
   کاربر می‌دهد ولی نگهبان نیست: مسیرِ تازه بی‌صدا به آن می‌افتد.

۳. مسیری که پیامِ «هنوز نیامده» می‌دهد، صفحه‌اش واقعاً نباید در
   `commonMain` باشد. این بند همان دروغِ بالا را می‌گیرد: روزی که
   `CuttingScreen` به `:core` بیاید، این بررسی می‌گوید سیمش کن —
   نه اینکه پیامِ کهنه سالِ دیگر هم بماند.

فهرست‌های استثنا عمداً دستی‌اند: جا ماندنِ یک صفحه باید یک **تصمیم**
باشد که کسی نوشته، نه سکوت.
"""
import re
import sys

import _src

# **جدا و با نام، نه از `_src.CORE`.** آن یکی هر سه منبعِ `:core` را
# یکی می‌بیند، و کلِ حرفِ این بررسی همان فرق است: صفحه‌ای که در
# `commonMain` باشد برای آیفون ساخته می‌شود، و صفحه‌ای که در
# `jvmAndroidMain` باشد نه.
COMMON = _src.COMMON / "com/afghanjama"
IOS = _src.IOS / "com/afghanjama/ios"
SCREENS = COMMON / "ui/screens"
ROUTES = COMMON / "ui/nav/Routes.kt"
SHELL = IOS / "IosShell.kt"

#: صفحه‌هایی که عمداً روی آیفون نیستند — با دلیل.
EXCLUDED = {
    "PostLoginQuoteScreen":
        "پردهٔ خوش‌آمدِ بینِ ورود و خانه. روی آیفون ورود مستقیم به "
        "خانه می‌رود؛ یک صفحهٔ اضافه سرِ هر بار باز کردنِ اپ، روی "
        "گوشی‌ای که ده بار در روز باز می‌شود، مزاحمت است نه خوش‌آمد.",
}

#: مسیرهایی که عمداً شاخه ندارند — با دلیل.
ROUTES_EXCLUDED = {
    "LOGIN":
        "پیش از پوسته رسیدگی می‌شود: تا وارد نشده باشید `IosShell` "
        "اصلاً به `when` نمی‌رسد و `LoginScreen` را مستقیم می‌کشد.",
    "POST_LOGIN":
        "صفحه‌اش در EXCLUDED است و دلیلش همان‌جا نوشته شده.",
}

#: مسیرهایی که پیامِ «هنوز نیامده» می‌دهند → صفحه‌ای که باید غایب بماند.
#:
#: کلید مسیر است و مقدار نامِ صفحه‌ای که اگر روزی به `commonMain`
#: بیاید، دیگر بهانه‌ای برای آن پیام نمی‌مانَد.
NOT_YET = {
    "CUTTING": "CuttingScreen",
    "ATTENDANCE": "AttendanceScreen",
    "WORKSHOP_LINK": "WorkshopLinkScreen",
}

for p in (SCREENS, ROUTES, SHELL, IOS):
    if not p.exists():
        print(f"✗ {p.name} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


ios = "".join(strip(p.read_text(encoding="utf-8")) for p in sorted(IOS.glob("*.kt")))
shell = strip(SHELL.read_text(encoding="utf-8"))

reachable = set(re.findall(r"\b(\w+Screen)\s*\(", ios))
available = {p.stem for p in SCREENS.glob("*.kt")}
if not available:
    print("✗ هیچ صفحه‌ای در commonMain پیدا نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

routes = set(re.findall(r"const val (\w+)", ROUTES.read_text(encoding="utf-8")))
if not routes:
    print("✗ هیچ مسیری در Routes.kt خوانده نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

# شاخه‌های `when` — و شاخهٔ چندمقصدی (`A, B, C ->`) هم باید باز شود،
# وگرنه دو مسیرِ رسیدگی‌شده «بی‌شاخه» گزارش می‌شوند.
handled = set()
for m in re.finditer(r"((?:Routes\.\w+\s*,\s*)*Routes\.\w+)\s*->", shell):
    handled |= set(re.findall(r"Routes\.(\w+)", m.group(1)))

fail = []

# ── ۱ هر صفحه یا در دسترس است یا استثنای نوشته‌شده ────────────────
for name in sorted(available):
    if name in reachable or name in EXCLUDED:
        continue
    fail.append(
        f"«{name}» در `commonMain` است ولی آیفون راهی به آن ندارد. "
        f"یا در `IosShell` سیمش کنید، یا در EXCLUDED با دلیل بنویسید."
    )

for name in sorted(EXCLUDED):
    if name not in available:
        fail.append(
            f"EXCLUDED نامِ «{name}» را دارد ولی چنین صفحه‌ای در "
            f"`commonMain` نیست — فهرست کهنه شده."
        )
    elif name in reachable:
        fail.append(
            f"«{name}» هم در EXCLUDED است هم واقعاً نشان داده می‌شود — "
            f"یکی‌شان باید برداشته شود."
        )

# ── ۲ هر مسیر یا شاخه دارد یا استثنای نوشته‌شده ───────────────────
for r in sorted(routes):
    if r in handled or r in ROUTES_EXCLUDED:
        continue
    fail.append(
        f"مسیرِ «{r}» شاخه‌ای در `IosShell` ندارد و بی‌صدا به `else` "
        f"می‌افتد. یا سیمش کنید، یا در ROUTES_EXCLUDED با دلیل."
    )

for r in sorted(ROUTES_EXCLUDED):
    if r not in routes:
        fail.append(
            f"ROUTES_EXCLUDED نامِ «{r}» را دارد ولی چنین مسیری در "
            f"`Routes` نیست — فهرست کهنه شده."
        )
    elif r in handled:
        fail.append(
            f"«{r}» هم در ROUTES_EXCLUDED است هم شاخه دارد — "
            f"یکی‌شان باید برداشته شود."
        )

# ── ۳ پیامِ «هنوز نیامده» باید هنوز راست باشد ─────────────────────
for r, screen in sorted(NOT_YET.items()):
    if r not in routes:
        fail.append(
            f"NOT_YET مسیرِ «{r}» را دارد ولی چنین مسیری نیست — "
            f"فهرست کهنه شده."
        )
        continue
    if r not in handled:
        fail.append(
            f"«{r}» قرار بود پیامِ «هنوز نیامده» بدهد ولی شاخه‌ای "
            f"ندارد — حالا به `else`ِ عمومی می‌افتد."
        )
    if (SCREENS / f"{screen}.kt").exists():
        fail.append(
            f"«{screen}» حالا در `commonMain` است، ولی مسیرِ «{r}» "
            f"هنوز می‌گوید به آیفون نیامده. سیمش کنید و پیام را "
            f"بردارید — همان اشکالی که دوخت و نظارت را دو کامیت "
            f"بسته نگه داشت."
        )

if fail:
    print(f"✗ {len(fail)} شکاف در صفحه‌های آیفون")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ آیفون: {len(available)} صفحهٔ commonMain — "
    f"{len(available) - len(EXCLUDED)} در دسترس، "
    f"{len(EXCLUDED)} استثنای نوشته‌شده؛ "
    f"{len(routes)} مسیر — {len(handled)} با شاخه، "
    f"{len(NOT_YET)} پیامِ «هنوز نیامده» که هنوز راست است"
)
