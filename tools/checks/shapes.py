#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""گوشه‌ها از یک پله‌بندی بیایند — `Radius` و `AppShapes` در `:core`.

**وضعی که از آن آمدیم (۱.۱۱).** اندروید `Shapes`ِ خودش را داشت (۶، ۱۰،
۱۴)، آیفون و کامپیوتر پیش‌فرضِ متریال را (۴، ۸، ۱۲، ۱۶)، و صفحه‌ها
گوشه‌های سرخود: ۱۰، ۱۱، ۱۴، ۱۸. همان کارت روی سه دستگاه سه گردی داشت.

این بررسی نگه می‌دارد که:
  • هر عددِ `RoundedCornerShape(N.dp)` روی پله‌های شبکهٔ ۴ باشد
    (۴، ۸، ۱۲، ۱۶، ۲۰، ۲۴، ۲۸) — بهتر از آن، خودِ `Radius.*`؛
  • هیچ `MaterialTheme(` جز در `AppTheme` ساخته نشود، تا گوشه، قلم،
    رنگ و راست‌به‌چپ روی هر سه سکو (و در آزمون‌های صفحه) یکی بماند؛
  • آیفون هر چهار وزنِ وزیرمتن را ثبت کند و در بسته بگذارد.
"""
import re
import sys

import _src

ALLOWED = {4, 8, 12, 16, 20, 24, 28}
SHAPE = re.compile(r"RoundedCornerShape\(\s*(\d+)\.dp\s*\)")
THEME = re.compile(r"(?<![A-Za-z0-9_.])MaterialTheme\(")

fail = []
checked = 0
roots = list(_src.CORE_ROOTS) + [_src.APP, _src.DESKTOP]
for root in roots:
    if not root.exists():
        continue
    for path in root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for i, line in enumerate(text.split("\n"), 1):
            st = line.lstrip()
            if st.startswith(("//", "*", "/*")):
                continue
            for m in SHAPE.finditer(line):
                checked += 1
                if int(m.group(1)) not in ALLOWED:
                    fail.append(f"{path.name}:{i} گوشهٔ {m.group(1)}dp بیرون از پله‌هاست — Radius.* بگذارید")
            if THEME.search(line) and path.name != "AppTheme.kt":
                fail.append(f"{path.name}:{i} MaterialTheme(… مستقیم — از AppTheme بگذرید")

ios_app = _src.find("ios/IosApp.kt")
ios_yml = (_src.REPO / "iosApp/project.yml").read_text(encoding="utf-8")
ios_src = ios_app.read_text(encoding="utf-8") if ios_app else ""
for w in ("regular", "medium", "semibold", "bold"):
    if f'bundledFont("vazirmatn_{w}")' not in ios_src:
        fail.append(f"آیفون وزنِ vazirmatn_{w} را ثبت نمی‌کند")
    if f"font/vazirmatn_{w}.ttf" not in ios_yml:
        fail.append(f"iosApp/project.yml فایلِ vazirmatn_{w}.ttf را در بسته نمی‌گذارد")

if fail:
    print(f"✗ {len(fail)} مشکل در گوشه‌ها و تم:")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)
print(f"✓ گوشه‌ها: هر {checked} عدد روی پله‌های Radius، هر تم از AppTheme، و آیفون هر چهار وزنِ وزیرمتن را دارد")
