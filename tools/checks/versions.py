#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""نسخهٔ گوشی و نسخهٔ ویندوز از هم نیفتند.

یک محصول است با دو بسته. اگر `versionName`ِ اندروید ۱.۱ باشد و
`packageVersion`ِ ویندوز ۱.۰.۰، پرسیدنِ «کدام نسخه را داری؟» بی‌جواب
می‌ماند — و همان سؤال دقیقاً وقتی پرسیده می‌شود که چیزی خراب شده و
باید بفهمیم کدام ساخت دستِ کارگاه است.

هیچ‌چیز جز آدم این دو را کنار هم نگه نمی‌دارد، و آدم یکی‌شان را
فراموش می‌کند. `jpackage` قالبِ سه‌بخشی می‌خواهد، پس ۱.۱ آنجا ۱.۱.۰
نوشته می‌شود؛ همین یک تفاوت اینجا پذیرفته است.

`versionCode` هم سنجیده می‌شود: عددی است که اندروید برای «آپدیت است یا
نه» نگاه می‌کند و اگر بالا نرود، نسخهٔ تازه روی نسخهٔ قبلی **نصب
نمی‌شود** بی‌آنکه پیامِ روشنی بدهد.
"""
import re
import sys

import _src

APP = _src.REPO / "app/build.gradle.kts"
DESK = _src.REPO / "desktop/build.gradle.kts"


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


app = strip(APP.read_text(encoding="utf-8"))
desk = strip(DESK.read_text(encoding="utf-8"))

fail = []

m_name = re.search(r"versionName\s*=\s*\"([^\"]+)\"", app)
m_code = re.search(r"versionCode\s*=\s*(\d+)", app)
m_pkg = re.search(r"packageVersion\s*=\s*\"([^\"]+)\"", desk)

if not m_name or not m_code:
    print("✗ versionName یا versionCode در app/build.gradle.kts پیدا نشد.")
    sys.exit(1)
if not m_pkg:
    print("✗ packageVersion در desktop/build.gradle.kts پیدا نشد.")
    sys.exit(1)

name, code, pkg = m_name.group(1), int(m_code.group(1)), m_pkg.group(1)

# ۱.۱ ↔ ۱.۱.۰ — سه بخشی کردن با صفر تنها تفاوتِ پذیرفته است.
parts = name.split(".")
if len(parts) == 2:
    expected = f"{name}.0"
elif len(parts) == 3:
    expected = name
else:
    expected = None
    fail.append(f"versionName «{name}» نه دو بخشی است نه سه بخشی.")

if expected and pkg != expected:
    fail.append(
        f"نسخهٔ اندروید «{name}» است و نسخهٔ ویندوز «{pkg}» — انتظار «{expected}»."
    )

if code < 2:
    fail.append(
        f"versionCode برابرِ {code} است. نسخهٔ ۱ روی گوشیِ کارگاه نصب شده؛ "
        f"ساختِ تازه با همان عدد روی آن **نمی‌نشیند**."
    )

# `jpackage` روی ویندوز نسخهٔ غیرِ عددی را رد می‌کند و خطایش گنگ است.
if not re.fullmatch(r"\d+\.\d+\.\d+", pkg):
    fail.append(f"packageVersion «{pkg}» قالبِ x.y.z ندارد؛ jpackage ردش می‌کند.")

if fail:
    print(f"✗ {len(fail)} مشکل در شماره‌گذاریِ نسخه")
    for f in fail:
        print(f"  • {f}")
    print("\n  هر دو فایل با هم بالا می‌روند: app/build.gradle.kts و")
    print("  desktop/build.gradle.kts. راهش در DELIVERY.md است.")
    sys.exit(1)

print(f"✓ نسخه: اندروید {name} (code {code}) و ویندوز {pkg} — هم‌خوان")
