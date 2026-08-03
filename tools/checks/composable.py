#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`@Composable`ی که سرِ جایش نیست.

**چرا لازم شد:** هنگامِ افزودنِ ویرایش و حذف به «اطلاعات پایه»، یک
`data class` و یک تابعِ تازه را **بینِ** `@Composable` و تابعی که به آن
تعلق داشت گذاشتم. نتیجه دو خطا در یک حرکت بود:

    @Composable                      ← حالا به data class چسبیده
    data class MasterRow(...)
    ...
    private fun SimpleListEditor(    ← و این بی‌حاشیه‌نویسی ماند

هیچ‌کدام از بررسی‌های قبلی این را نمی‌دید: ایمپورت‌ها درست بودند، نامی
تکراری نبود، ماژولی جابه‌جا نشده بود. فقط کامپایلر بعد از دو دقیقه در CI
گفت «This annotation is not applicable to target 'class'».

درج‌کردنِ کد بینِ حاشیه‌نویسی و تابعش دقیقاً همان کاری است که هنگامِ
اضافه‌کردن به وسطِ فایل زیاد پیش می‌آید — و چون KDoc بینشان می‌نشیند،
چشم فاصله را طبیعی می‌بیند.

**حدِ این بررسی:** فقط نیمهٔ اولِ ماجرا را می‌گیرد — حاشیه‌نویسیِ بی‌جا.
نیمهٔ دوم («تابعی که Composable صدا می‌زند ولی حاشیه‌نویسی ندارد») به
تحلیلِ نوع نیاز دارد و از دستِ یک اسکریپتِ متنی برنمی‌آید. ولی چون هر دو
از **یک** درج می‌آیند، گرفتنِ اولی جلوی دومی را هم می‌گیرد.
"""
import re
import sys

import _src

#: خطی که فقط `@Composable` است (نه `content: @Composable () -> Unit` که
#: کاربردِ درست و درون‌خطیِ همین نام است)
ANN = re.compile(r"^\s*@Composable\s*$")

#: حاشیه‌نویسیِ دیگری که می‌تواند بینشان بنشیند — `@OptIn`، `@Preview`، …
OTHER_ANN = re.compile(r"^\s*@\w")

#: چیزی که **می‌تواند** `@Composable` بگیرد: تابع، یا ویژگی (getterِ آن)
OK = re.compile(r"^\s*(?:(?:private|internal|public|protected|inline|noinline|"
                r"crossinline|expect|actual|external|suspend|operator|infix|"
                r"tailrec)\s+)*(?:fun|val|var|get)\b")


def landing(lines, i):
    """اولین خطِ معنادار بعد از خطِ `i` — از رویِ توضیح و حاشیه‌نویسی رد می‌شود."""
    j = i + 1
    in_kdoc = False
    while j < len(lines):
        s = lines[j].strip()
        if in_kdoc:
            if "*/" in s:
                in_kdoc = False
            j += 1
            continue
        if not s or s.startswith("//"):
            j += 1
            continue
        if s.startswith("/*"):
            if "*/" not in s:
                in_kdoc = True
            j += 1
            continue
        if OTHER_ANN.match(lines[j]):
            j += 1
            continue
        return j
    return None


files = _src.kt_files()
if not files:
    print("✗ هیچ فایلی پیدا نشد — بررسی پوچ بود، مسیرها را ببینید")
    sys.exit(1)

bad = []
for p in files:
    lines = p.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if not ANN.match(line):
            continue
        j = landing(lines, i)
        if j is None:
            bad.append((p.name, i + 1, "«@Composable» آخرِ فایل، به چیزی نچسبیده"))
        elif not OK.match(lines[j]):
            bad.append((p.name, i + 1, f"خط {j + 1}: {lines[j].strip()[:60]}"))

if bad:
    print(f"✗ {len(bad)} حاشیه‌نویسیِ @Composable روی چیزی که تابع نیست")
    for f, ln, what in bad:
        print(f"  {f}:{ln}  →  {what}")
    print("\n  احتمالاً چیزی بینِ @Composable و تابعش درج شده؛ آن‌وقت هم این")
    print("  حاشیه‌نویسی بی‌جا می‌شود و هم تابعِ اصلی بی‌حاشیه‌نویسی می‌ماند.")
    sys.exit(1)

print(f"✓ {len(files)} فایل — هر @Composable روی یک تابع نشسته")
