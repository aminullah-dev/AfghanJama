#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
نامِ تکراری در بررسی‌های خودآزمایی.

صفحهٔ خودآزمایی ردیف‌ها را در LazyColumn نشان می‌دهد. تا وقتی کلید از
«گروه + نام» ساخته می‌شد، دو بررسیِ هم‌نام کلیدِ تکراری می‌ساختند و کلِ
اپ می‌افتاد:

    Key "چرخهٔ سفارش-هیچ سندِ ناترازی در این حذف نبود" was already used

کلید حالا از شمارهٔ ردیف می‌آید و دیگر نمی‌اندازد، ولی نامِ تکراری هنوز
گزارش را گمراه‌کننده می‌کند: کاربر نمی‌فهمد کدام‌یک رد شده. اینجا جلویش
گرفته می‌شود.
"""
import pathlib as _pl
import re
import sys
from collections import defaultdict

_REPO = _pl.Path(__file__).resolve().parents[2]
SRC = _REPO / "app/src/main/java/com/afghanjama/selftest/SelfTest.kt"
if not SRC.exists():
    raise SystemExit(f"✗ {SRC} نیست — مسیر اشتباه است، بررسی پوچ بود")

text = SRC.read_text(encoding="utf-8")
text = re.sub(r"//[^\n]*", " ", text)
text = re.sub(r"/\*(?:.|\n)*?\*/", " ", text)

# هر تابعِ بررسی یک CheckSink با نامِ گروه می‌سازد
groups = re.split(r"\bfun\s+check\w+\s*\(", text)
if len(groups) < 5:
    raise SystemExit(f"✗ فقط {len(groups) - 1} تابعِ بررسی دیده شد — الگو عوض شده")

problems = []
for block in groups[1:]:
    g = re.search(r'CheckSink\(\s*"([^"]+)"', block)
    if not g:
        continue
    group = g.group(1)
    names = defaultdict(int)
    # skip عمداً شمرده نمی‌شود: الگوی رایجِ این فایل این است که وقتی
    # داده در دسترس نیست، همان ادعا با همان نام skip شود و بعد return.
    # آن دو هرگز با هم اجرا نمی‌شوند، پس تکراری نیستند.
    for m in re.finditer(r'\bs\.(?:eq|isTrue|isFalse)\(\s*\n?\s*"([^"]+)"', block):
        names[m.group(1)] += 1
    for name, n in names.items():
        if n > 1:
            problems.append((group, name, n))

if problems:
    print(f"✗ {len(problems)} نامِ تکراری")
    for group, name, n in problems:
        print(f"  [{group}] «{name}» — {n} بار")
    sys.exit(1)
total = sum(1 for _ in re.finditer(r'\bs\.(?:eq|isTrue|isFalse|skip)\(', text))
print(f"✓ {total} ادعا در {len(groups) - 1} بررسی — هیچ نامِ تکراری نیست")
