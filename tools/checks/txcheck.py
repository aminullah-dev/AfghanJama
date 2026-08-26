#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر عملیاتی که هم‌زمان چند دفتر را می‌نویسد باید اتمی باشد.

**اشکالی که این بررسی نگه می‌دارد:**

یک فاکتورِ فروش حدودِ ده نوشتنِ مستقل انجام می‌دهد — انبار، نقد، دفترِ
مشتری، سند، حسابرسی، و دو ژورنال. اگر پروسه وسطِ کار بمیرد (باتری،
کشته شدن به‌خاطرِ حافظه، کرش) دفتر **نیمه‌نوشته** می‌مانَد: انبار کم
شده ولی پول ثبت نشده. روی اندروید این فرضی نیست.

بررسی دو کار می‌کند:

  ۱ هر تابعی که در `Repo` بیش از یک «ناحیهٔ دفتر» را می‌نویسد باید یا
    پیچیده شده باشد (`db.atomic { … }`) یا در فهرستِ صریحِ باقی‌مانده
    باشد.
  ۲ **فهرستِ باقی‌مانده فقط می‌تواند کوچک شود.** اگر تابعی که یک بار
    اتمی شده دوباره از حالت خارج شود، اینجا قرمز می‌شود.

فهرستِ باقی‌مانده عمداً در خودِ این فایل است و نه در جای دیگری: هر بار
که کسی نگاهش می‌کند، می‌بیند چقدر کار مانده.
"""
import pathlib
import re
import sys

import _src

REPO = _src.CORE / "com/afghanjama/data/repo/Repo.kt"

#: نواحیِ دفتر. نوشتن در دو تای اینها یعنی عملیات باید اتمی باشد.
AREAS = {
    "انبار": r"(materialStockDao|finishedStockDao|stockMovementDao|"
             r"addFinishedStock|adjustFinishedStock)\s*\(",
    "پول": r"\b(income|spend)\s*\(",
    "دفتر": r"\bpostLedger\s*\(",
    "ژورنال": r"\bpostJournal\s*\(",
    "سند": r"\bcreateDocument\s*\(",
}

WRITE = re.compile(
    r"\b(income|spend|postLedger|postJournal|createDocument)\s*\(|"
    r"\.(insert|update|delete)\w*\s*\(|execSQL"
)

#: هنوز اتمی نشده‌اند. **این فهرست فقط کوتاه‌تر می‌شود.**
#:
#: ترتیبِ کار: از پرخطرترین به کم‌خطرترین — هرچه ناحیهٔ بیشتری را لمس
#: کند، نیمه‌ماندنش گران‌تر است.
PENDING: set[str] = set()
#
# **خالی شد.** هر ۲۰ عملیاتِ چندناحیه‌ای اتمی است.
#
# اگر روزی نامی اینجا اضافه شد، یعنی کسی عملیاتِ پولیِ تازه‌ای نوشته و
# اتمی نکرده. آن نام باید با دلیل بیاید، نه بی‌صدا.


def members(src):
    """هر عضوِ سطحِ کلاس، از اعلانش تا اعلانِ بعدی."""
    marks = [
        (m.start(), m.group(1), "suspend fun" in src[m.start():m.start() + 45])
        for m in re.finditer(r"\n    (?:private )?(?:suspend )?(?:fun|val) (\w+)", src)
    ]
    for i, (pos, name, sus) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(src)
        yield name, src[pos:end], sus


src = REPO.read_text(encoding="utf-8")
if "class Repo" not in src:
    print(f"✗ {REPO} شبیهِ Repo نیست — بررسی پوچ می‌شد.")
    sys.exit(1)

# الگوی پوسته/بدنه باید شناخته شود، وگرنه بررسی کارِ انجام‌شده را
# نمی‌بیند: بعد از پیچیدن، `sellInvoice` یک خط می‌شود و هیچ نوشتنی
# ندارد، و نوشتن‌ها به `sellInvoiceTx` می‌روند. نسخهٔ اولِ این بند
# «۰ از ۱۸» گزارش داد در حالی که دو تا اتمی شده بودند.
all_bodies = {name: body for name, body, _ in members(src)}
shells = {
    name: body for name, body, sus in members(src)
    if sus and re.search(r"db\.atomic\s*\{\s*" + re.escape(name) + r"Tx\(", body)
}

multi, wrapped, unwrapped = [], [], []
for name, body, sus in members(src):
    if not sus or name.endswith("Tx"):
        continue
    # نوشتن‌ها یا در خودِ تابع‌اند یا — اگر پیچیده شده — در بدنهٔ `…Tx`
    effective = body + all_bodies.get(name + "Tx", "")
    if not WRITE.search(effective):
        continue
    areas = [k for k, p in AREAS.items() if re.search(p, effective)]
    if len(areas) < 2:
        continue
    multi.append(name)
    if name in shells:
        wrapped.append(name)
    else:
        unwrapped.append(name)

if not multi:
    print("✗ هیچ عملیاتِ چندناحیه‌ای پیدا نشد — بررسی پوچ شد، الگوها را ببینید.")
    sys.exit(1)

fail = []

# ۱ — چیزی که اتمی نیست باید صریح در فهرستِ باقی‌مانده باشد
for n in sorted(set(unwrapped) - PENDING):
    fail.append(
        f"«{n}» چند دفتر را می‌نویسد ولی اتمی نیست و در فهرستِ باقی‌مانده هم "
        f"نیست. یا در `db.atomic { '{' } … { '}' }` بپیچیدش، یا اگر عمدی است "
        f"به PENDING اضافه کن."
    )

# ۲ — فهرست فقط کوچک می‌شود؛ عقب‌گرد ممنوع
for n in sorted(PENDING & set(wrapped)):
    fail.append(
        f"«{n}» اتمی شده ولی هنوز در فهرستِ باقی‌مانده است — از PENDING برش دار."
    )

# ۳ — نامی که دیگر وجود ندارد فهرست را دروغین می‌کند
for n in sorted(PENDING - set(multi)):
    fail.append(
        f"«{n}» در فهرستِ باقی‌مانده است ولی دیگر عملیاتِ چندناحیه‌ای نیست — "
        f"از PENDING برش دار."
    )

if fail:
    print(f"✗ {len(fail)} مشکل در مرزِ تراکنش")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ تراکنش: {len(wrapped)} از {len(multi)} عملیاتِ چندناحیه‌ای اتمی است، "
    f"{len(unwrapped)} در نوبت"
)
