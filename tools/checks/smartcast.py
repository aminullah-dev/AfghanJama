#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""smart-cast روی ویژگیِ nullableِ ماژولِ دیگر کار نمی‌کند.

کاتلین وقتی `x.foo != null` را می‌بیند، در همان بلوک `x.foo` را ناتهی
می‌گیرد — **ولی نه اگر `foo` ویژگیِ عمومیِ ماژولِ دیگری باشد.** آنجا
کامپایلر نمی‌تواند ضمانت کند که بینِ دو خط عوض نشده، پس خطا می‌دهد.

این تله دو بار در همین پروژه افتاد و هر بار یک رفت‌وبرگشتِ CI برد:

- فاز ۴: `StockForecast.daysLeft` و `EmployeeAttendance.since` وقتی
  ۲۷ ViewModel به `:core` رفتند.
- فاز ۶: `BoardRow.dueIn` وقتی `BoardRow` به `:core` رفت.

بارِ اول اسکریپتش را دستی نوشتم و پیش از جابه‌جایی اجرا کردم؛ بارِ دوم
یادم رفت. ابزاری که فقط وقتی یادت باشد کار می‌کند، ابزار نیست.

**این بررسی محافظه‌کار است:** الگوی «گاردِ null و بعد استفادهٔ مستقیم»
را می‌گیرد. `?.` و `!!` امن‌اند و نادیده گرفته می‌شوند.
"""
import re
import sys

import _src

CORE = _src.ROOTS[0]

#: نامِ ویژگی → (کلاس، فایل) برای هر ویژگیِ nullableِ اعلام‌شده در :core
NULLABLE = {}
core_files = sorted(CORE.rglob("*.kt"))
for p in core_files:
    cls = None
    for line in p.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*(?:data |sealed |open |abstract )*class\s+(\w+)", line)
        if m:
            cls = m.group(1)
        for mm in re.finditer(r"\bva[lr]\s+(\w+)\s*:\s*[\w.<>]+\?", line):
            NULLABLE[mm.group(1)] = (cls, p.name)

if not core_files:
    print("✗ هیچ فایلی در :core نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

GUARD = re.compile(r"(\w+)\.(\w+)\s*(?:!=|==)\s*null")

risky = []
for root in _src.ROOTS[1:]:            # هر ماژولی جز :core
    for p in sorted(root.rglob("*.kt")):
        lines = p.read_text(encoding="utf-8").splitlines()
        for i, line in enumerate(lines):
            m = GUARD.search(line)
            if not m:
                continue
            recv, prop = m.group(1), m.group(2)
            if prop not in NULLABLE:
                continue
            # از **همین‌جا** تا دوازده خطِ بعد.
            #
            # نسخهٔ اول کلِ خطِ اول را می‌برید و همان باعث شد اشکالِ
            # واقعی (`row.dueIn != null && row.dueIn < 0` در یک خط) را
            # نگیرد. بیشترِ گاردها دقیقاً همین شکل‌اند.
            window = line[m.end():] + "\n" + "\n".join(lines[i + 1:i + 12])
            direct = re.search(
                rf"(?<![?!.]){re.escape(recv)}\.{re.escape(prop)}\b(?!\s*[!?]|\s*[!=]=)",
                window
            )
            if direct:
                cls, owner = NULLABLE[prop]
                risky.append((p.name, i + 1, f"{recv}.{prop}", cls, owner))

if risky:
    print(f"✗ {len(risky)} smart-cast روی ویژگیِ ماژولِ دیگر")
    for f, i, expr, cls, owner in risky:
        print(f"  {f}:{i}  {expr}   ({cls} در {owner} — :core)")
    print("\n  کاتلین اینجا smart-cast نمی‌کند. مقدار را در یک متغیرِ")
    print("  محلی بگیرید: `val v = x.foo` و بعد روی `v` شرط بگذارید.")
    sys.exit(1)

print(f"✓ {len(NULLABLE)} ویژگیِ nullableِ :core — هیچ smart-castِ بین‌ماژولی نمانده")
