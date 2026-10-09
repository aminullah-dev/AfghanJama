#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر نوشتنی در `Repo` از درِ لایسنس می‌گذرد — و پیش از اولین نوشتن.

**چرا لازم شد.** از نسخهٔ لایسنس‌دار، کارگاهِ بی‌لایسنس پس از دورهٔ
آزمایشی «فقط‌خواندنی» است: دیدن، جست‌وجو، چاپ، خروجی و پشتیبان بله؛
ثبت و تغییر نه. این تصمیم در لایهٔ سرویس گرفته می‌شود (`Repo`)، نه در
صفحه — دکمه‌ای که در صفحه پنهان نشده باشد هم به همان در می‌خورد.

ولی `Repo` صد تابعِ نویسنده دارد و هر ماه یکی اضافه می‌شود. کافی است
یکی فراموش کند `LicenceGate.requireWrite()` را صدا بزند تا کارگاهِ
فقط‌خواندنی از همان در سفارش و پول ثبت کند — بی هیچ خطای کامپایلی و
بی هیچ آزمونِ قرمزی.

سه بند:

  ۱ هر تابعِ عمومیِ `Repo` که می‌نویسد (خودش یا بدنهٔ `…Tx`اش) باید
    `LicenceGate.` یا `writes {` داشته باشد — یا در [EXEMPT] با دلیل.
  ۲ آن در باید **پیش از** اولین نوشتن باشد؛ دری که بعد از نوشتن بسته
    شود فقط پیامِ خطا می‌دهد و داده را نوشته.
  ۳ سرورِ کارگاه (`LanServer`) پیش از نشاندنِ درخواستِ گوشیِ کارگر
    می‌پرسد — وگرنه کارگر از راهِ دستگاهِ اصلیِ فقط‌خواندنی می‌نوشت.
"""
import re
import sys

import _src

REPO = _src.CORE / "com/afghanjama/data/repo/Repo.kt"
LAN = _src.CORE / "com/afghanjama/lan/LanServer.kt"

#: نوشتن‌هایی که عمداً از درِ لایسنس نمی‌گذرند — با دلیل.
EXEMPT = {
    "repairManualCashClassification":
        "تعمیرِ یک‌بارهٔ طبقه‌بندی سرِ راه‌اندازی؛ سندی نمی‌سازد و باید روی "
        "دفترِ فقط‌خواندنی هم درست بماند.",
    "pruneEvents":
        "جاروی رویدادهای پردازش‌شدهٔ قدیمی؛ نگه‌داریِ خودِ دفتر است نه سندِ کارگاه.",
    "markCustomerNotified":
        "فقط ثبت می‌کند که به مشتری پیام یا زنگ رفت — بخشی از فرستادن در "
        "واتساپ، که پروتکل می‌گوید همیشه کار می‌کند.",
    "writeLicenceTrialAnchor":
        "دفترداریِ خودِ لایسنس (شروعِ دورهٔ آزمایشی)؛ باید روی دستگاهِ "
        "فقط‌خواندنی هم بنشیند، وگرنه نصبِ دوباره دوره را از صفر شروع می‌کرد.",
}

WRITE = re.compile(
    r"\b(income|spend|postLedger|postJournal|createDocument|audit|emit)\s*\(|"
    r"\.(insert|update|upsert|delete|clear|prune)\w*\s*\(|execSQL"
)
GATE = re.compile(r"LicenceGate\.|\bwrites\s*\{")


def members(src):
    """هر عضوِ سطحِ کلاس، از اعلانش تا اعلانِ بعدی — همان برشِ `txcheck`."""
    marks = [
        (m.start(), m.group(2), m.group(1) is not None,
         "suspend fun" in src[m.start():m.start() + 60])
        for m in re.finditer(r"\n    (private |internal )?(?:inline )?(?:suspend )?(?:fun|val) (?:<[^>]+> )?(\w+)", src)
    ]
    for i, (pos, name, private, sus) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(src)
        yield name, src[pos:end], private, sus


def strip_comments(body):
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return re.sub(r"//[^\n]*", "", body)


src = REPO.read_text(encoding="utf-8")
if "class Repo" not in src:
    print(f"✗ {REPO} شبیهِ Repo نیست — بررسی پوچ می‌شد.")
    sys.exit(1)

all_members = list(members(src))
bodies = {n: strip_comments(b) for n, b, _, _ in all_members}

writers, gated, fail = [], [], []
for name, raw, private, sus in all_members:
    if private or not sus or name.endswith("Tx"):
        continue
    # اعلانِ خودِ تابع کنار می‌رود، وگرنه `fun income(` خودش «نوشتن» خوانده می‌شد.
    body = bodies[name].replace(f"fun {name}(", "fun (", 1)
    effective = body + bodies.get(name + "Tx", "")
    if not WRITE.search(effective):
        continue
    writers.append(name)
    if name in EXEMPT:
        continue
    g = GATE.search(body)
    if not g:
        fail.append(f"«{name}» می‌نویسد ولی از درِ لایسنس نمی‌گذرد — "
                    f"`LicenceGate.requireWrite()` را اولِ بدنه بگذارید، یا اگر عمدی است "
                    f"با دلیل به EXEMPT اضافه کنید.")
        continue
    w = WRITE.search(body)
    if w and w.start() < g.start():
        fail.append(f"«{name}» پیش از درِ لایسنس می‌نویسد — در باید پیش از اولین نوشتن باشد.")
        continue
    gated.append(name)

for n in sorted(set(EXEMPT) - set(writers)):
    fail.append(f"«{n}» در EXEMPT است ولی دیگر نویسنده‌ای به این نام نیست — برش دارید.")

if len(writers) < 50:
    fail.append(f"فقط {len(writers)} نویسنده پیدا شد — الگوها شکسته‌اند و بررسی پوچ می‌شد.")

# ۳ — سرورِ کارگاه
lan = LAN.read_text(encoding="utf-8")
m = re.search(r"private suspend fun acceptRequest\(.*?\n    }\n", lan, re.S)
if not m:
    fail.append("`LanServer.acceptRequest` پیدا نشد — بررسیِ مسیرِ شبکه پوچ می‌شد.")
else:
    body = m.group(0)
    gi = body.find("LicenceGate.")
    wi = body.find(".insert(")
    if gi < 0:
        fail.append("`LanServer.acceptRequest` درخواستِ کارگر را بی پرسیدن از لایسنس می‌نشاند.")
    elif 0 <= wi < gi:
        fail.append("`LanServer.acceptRequest` پیش از پرسیدن از لایسنس می‌نویسد.")

if fail:
    print(f"✗ {len(fail)} مشکل در درِ لایسنس")
    for f in fail:
        print("  • " + f)
    sys.exit(1)

print(f"✓ لایسنس: هر {len(gated)} نویسندهٔ Repo پیش از نوشتن می‌پرسد "
      f"({len(EXEMPT)} استثنای دلیل‌دار)، و سرورِ کارگاه هم")
